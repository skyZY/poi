/* ====================================================================
   Licensed to the Apache Software Foundation (ASF) under one or more
   contributor license agreements.  See the NOTICE file distributed with
   this work for additional information regarding copyright ownership.
   The ASF licenses this file to You under the Apache License, Version 2.0
   (the "License"); you may not use this file except in compliance with
   the License.  You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
==================================================================== */

package org.apache.poi.hwmf.record;

import java.awt.Shape;
import java.awt.geom.Arc2D;
import java.awt.geom.Area;
import java.awt.geom.Dimension2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.PathIterator;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.poi.hwmf.draw.HwmfGraphics;
import org.apache.poi.hwmf.draw.HwmfGraphics.FillDrawStyle;
import org.apache.poi.util.Internal;
import org.apache.poi.util.LittleEndianConsts;
import org.apache.poi.util.LittleEndianInputStream;

public class HwmfDraw {
    /**
     * The META_MOVETO record sets the output position in the playback device context to a specified
     * point.
     */
    public static class WmfMoveTo implements HwmfRecord {

        protected final Point2D point = new Point2D.Double();

        @Override
        public HwmfRecordType getWmfRecordType() {
            return HwmfRecordType.moveTo;
        }

        @Override
        public int init(LittleEndianInputStream leis, long recordSize, int recordFunction) throws IOException {
            return readPointS(leis, point);
        }

        @Override
        public void draw(HwmfGraphics ctx) {
            ctx.getProperties().setLocation(point);
        }

        @Override
        public String toString() {
            return pointToString(point);
        }
    }

    /**
     * The META_LINETO record draws a line from the drawing position that is defined in the playback
     * device context up to, but not including, the specified point.
     */
    public static class WmfLineTo implements HwmfRecord {

        protected final Point2D point = new Point2D.Double();

        @Override
        public HwmfRecordType getWmfRecordType() {
            return HwmfRecordType.lineTo;
        }

        @Override
        public int init(LittleEndianInputStream leis, long recordSize, int recordFunction) throws IOException {
            return readPointS(leis, point);
        }

        @Override
        public void draw(HwmfGraphics ctx) {
            Point2D start = ctx.getProperties().getLocation();
            Line2D line = new Line2D.Double(start, point);
            ctx.draw(line);
            ctx.getProperties().setLocation(point);
        }

        @Override
        public String toString() {
            return pointToString(point);
        }
    }

    /**
     * The META_POLYGON record paints a polygon consisting of two or more vertices connected by
     * straight lines. The polygon is outlined by using the pen and filled by using the brush and polygon fill
     * mode that are defined in the playback device context.
     */
    public static class WmfPolygon implements HwmfRecord {

        protected Path2D poly;

        @Override
        public HwmfRecordType getWmfRecordType() {
            return HwmfRecordType.polygon;
        }

        @Override
        public int init(LittleEndianInputStream leis, long recordSize, int recordFunction) throws IOException {
            /**
             * A 16-bit signed integer that defines the number of points in the array.
             */
            int numberofPoints = leis.readShort();

            poly = new Path2D.Double(Path2D.WIND_EVEN_ODD, numberofPoints);
            for (int i=0; i<numberofPoints; i++) {
                // A 16-bit signed integer that defines the horizontal (x) coordinate of the point.
                int x = leis.readShort();
                // A 16-bit signed integer that defines the vertical (y) coordinate of the point.
                int y = leis.readShort();
                if (i==0) {
                    poly.moveTo(x, y);
                } else {
                    poly.lineTo(x, y);
                }
            }

            return LittleEndianConsts.SHORT_SIZE+numberofPoints*LittleEndianConsts.INT_SIZE;
        }

        @Override
        public void draw(HwmfGraphics ctx) {
            Path2D p = (Path2D)poly.clone();
            // don't close the path
            p.setWindingRule(ctx.getProperties().getWindingRule());
            switch (getFillDrawStyle()) {
                case FILL:
                    ctx.fill(p);
                    break;
                case DRAW:
                    ctx.draw(p);
                    break;
                case FILL_DRAW:
                    ctx.fill(p);
                    ctx.draw(p);
                    break;
            }
        }

        @Override
        public String toString() {
            return "{ poly: "+polyToString(poly)+" }";
        }

        /**
         * @return true, if the shape should be filled
         */
        protected FillDrawStyle getFillDrawStyle() {
            return FillDrawStyle.FILL;
        }
    }

    /**
     * The META_POLYLINE record draws a series of line segments by connecting the points in the
     * specified array.
     */
    public static class WmfPolyline extends WmfPolygon {

        @Override
        public HwmfRecordType getWmfRecordType() {
            return HwmfRecordType.polyline;
        }

        @Override
        protected FillDrawStyle getFillDrawStyle() {
            return FillDrawStyle.DRAW;
        }
    }

    /**
     * The META_ELLIPSE record draws an ellipse. The center of the ellipse is the center of the specified
     * bounding rectangle. The ellipse is outlined by using the pen and is filled by using the brush; these
     * are defined in the playback device context.
     */
    public static class WmfEllipse implements HwmfRecord {
        protected final Rectangle2D bounds = new Rectangle2D.Double();

        @Override
        public HwmfRecordType getWmfRecordType() {
            return HwmfRecordType.ellipse;
        }

        @Override
        public int init(LittleEndianInputStream leis, long recordSize, int recordFunction) throws IOException {
            return readBounds(leis, bounds);
        }

        @Override
        public void draw(HwmfGraphics ctx) {
            ctx.fill(getShape());
        }

        protected Ellipse2D getShape() {
            return new Ellipse2D.Double(bounds.getX(), bounds.getY(), bounds.getWidth(), bounds.getHeight());
        }

        @Override
        public String toString() {
            return boundsToString(bounds);
        }
    }


    /**
     * The META_FRAMEREGION record draws a border around a specified region using a specified brush.
     */
    public static class WmfFrameRegion implements HwmfRecord {
        /**
         * A 16-bit unsigned integer used to index into the WMF Object Table to get
         * the region to be framed.
         */
        protected int regionIndex;
        /**
         * A 16-bit unsigned integer used to index into the WMF Object Table to get the
         * Brush to use for filling the region.
         */
        protected int brushIndex;
        /**
         * A 16-bit signed integer that defines the height, in logical units, of the
         * region frame.
         */
        protected int height;
        /**
         * A 16-bit signed integer that defines the width, in logical units, of the
         * region frame.
         */
        protected int width;

        @Override
        public HwmfRecordType getWmfRecordType() {
            return HwmfRecordType.frameRegion;
        }

        @Override
        public int init(LittleEndianInputStream leis, long recordSize, int recordFunction) throws IOException {
            regionIndex = leis.readUShort();
            brushIndex = leis.readUShort();
            height = leis.readShort();
            width = leis.readShort();
            return 4*LittleEndianConsts.SHORT_SIZE;
        }

        @Override
        public void draw(HwmfGraphics ctx) {
            ctx.applyObjectTableEntry(brushIndex);
            ctx.applyObjectTableEntry(regionIndex);
            Rectangle2D inner = ctx.getProperties().getRegion().getBounds();
            double x = inner.getX()-width;
            double y = inner.getY()-height;
            double w = inner.getWidth()+2.0*width;
            double h = inner.getHeight()+2.0*height;
            Rectangle2D outer = new Rectangle2D.Double(x,y,w,h);
            Area frame = new Area(outer);
            frame.subtract(new Area(inner));
            ctx.fill(frame);
        }
    }

    /**
     * The META_POLYPOLYGON record paints a series of closed polygons. Each polygon is outlined by
     * using the pen and filled by using the brush and polygon fill mode; these are defined in the playback
     * device context. The polygons drawn by this function can overlap.
     */
    public static class WmfPolyPolygon implements HwmfRecord {

        protected final List<Path2D> polyList = new ArrayList<>();

        @Override
        public HwmfRecordType getWmfRecordType() {
            return HwmfRecordType.polyPolygon;
        }

        @Override
        public int init(LittleEndianInputStream leis, long recordSize, int recordFunction) throws IOException {
            // see http://secunia.com/gfx/pdf/SA31675_BA.pdf ;)
            /**
             * A 16-bit unsigned integer that defines the number of polygons in the object.
             */
            int numberOfPolygons = leis.readUShort();
            /**
             * A NumberOfPolygons array of 16-bit unsigned integers that define the number of
             * points for each polygon in the object.
             */
            int[] pointsPerPolygon = new int[numberOfPolygons];

            int size = LittleEndianConsts.SHORT_SIZE;

            for (int i=0; i<numberOfPolygons; i++) {
                pointsPerPolygon[i] = leis.readUShort();
                size += LittleEndianConsts.SHORT_SIZE;
            }

            for (int nPoints : pointsPerPolygon) {
                /**
                 * An array of 16-bit signed integers that define the coordinates of the polygons.
                 * (Note: MS-WMF wrongly says unsigned integers ...)
                 */
                Path2D poly = new Path2D.Double(Path2D.WIND_EVEN_ODD, nPoints);
                for (int i=0; i<nPoints; i++) {
                    int x = leis.readShort();
                    int y = leis.readShort();
                    size += 2*LittleEndianConsts.SHORT_SIZE;
                    if (i == 0) {
                        poly.moveTo(x, y);
                    } else {
                        poly.lineTo(x, y);
                    }
                }
                poly.closePath();
                polyList.add(poly);
            }

            return size;
        }

        @Override
        public void draw(HwmfGraphics ctx) {
            Shape shape = getShape(ctx);
            if (shape == null) {
                return;
            }

            switch (getFillDrawStyle()) {
                case DRAW:
                    ctx.draw(shape);
                    break;
                case FILL:
                    ctx.fill(shape);
                    break;
                case FILL_DRAW:
                    ctx.fill(shape);
                    ctx.draw(shape);
                    break;
            }
        }

        protected FillDrawStyle getFillDrawStyle() {
            // Each polygon SHOULD be outlined using the current pen, and filled using the current brush and
            // polygon fill mode that are defined in the playback device context. The polygons defined by this
            // record can overlap.
            return FillDrawStyle.FILL_DRAW;
        }

        /**
         * @return true, if a polyline should be closed, i.e. is a polygon
         */
        protected boolean isClosed() {
            return true;
        }

        protected Shape getShape(HwmfGraphics ctx) {
            int windingRule = ctx.getProperties().getWindingRule();

            if (isClosed()) {
                Area area = null;
                for (Path2D poly : polyList) {
                    Path2D p = (Path2D)poly.clone();
                    p.setWindingRule(windingRule);
                    Area newArea = new Area(p);
                    if (area == null) {
                        area = newArea;
                    } else {
                        area.exclusiveOr(newArea);
                    }
                }
                return area;
            } else {
                Path2D path = new Path2D.Double();
                path.setWindingRule(windingRule);
                for (Path2D poly : polyList) {
                    path.append(poly, false);
                }
                return path;
            }
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder();
            sb.append("{ polyList: [");
            boolean isFirst = true;
            for (Path2D p : polyList) {
                if (!isFirst) {
                    sb.append(",");
                }
                isFirst = false;
                sb.append("{ points: ");
                sb.append(polyToString(p));
                sb.append(" }");
            }
            sb.append(" }");
            return sb.toString();
        }
    }

    /**
     * The META_RECTANGLE record paints a rectangle. The rectangle is outlined by using the pen and
     * filled by using the brush that are defined in the playback device context.
     */
    public static class WmfRectangle implements HwmfRecord {
        protected final Rectangle2D bounds = new Rectangle2D.Double();

        @Override
        public HwmfRecordType getWmfRecordType() {
            return HwmfRecordType.frameRegion;
        }

        @Override
        public int init(LittleEndianInputStream leis, long recordSize, int recordFunction) throws IOException {
            return readBounds(leis, bounds);
        }

        @Override
        public void draw(HwmfGraphics ctx) {
            ctx.fill(bounds);
        }

        @Override
        public String toString() {
            return boundsToString(bounds);
        }
    }

    /**
     * The META_SETPIXEL record sets the pixel at the specified coordinates to the specified color.
     */
    public static class WmfSetPixel implements HwmfRecord {
        /**
         * A ColorRef Object that defines the color value.
         */
        protected final HwmfColorRef colorRef = new HwmfColorRef();

        protected final Point2D point = new Point2D.Double();


        @Override
        public HwmfRecordType getWmfRecordType() {
            return HwmfRecordType.setPixel;
        }

        @Override
        public int init(LittleEndianInputStream leis, long recordSize, int recordFunction) throws IOException {
            int size = colorRef.init(leis);
            return size+ readPointS(leis, point);
        }

        @Override
        public void draw(HwmfGraphics ctx) {
            Shape s = new Rectangle2D.Double(point.getX(), point.getY(), 1, 1);
            ctx.fill(s);
        }
    }

    /**
     * The META_ROUNDRECT record paints a rectangle with rounded corners. The rectangle is outlined
     * using the pen and filled using the brush, as defined in the playback device context.
     */
    public static class WmfRoundRect implements HwmfRecord {
        /**
         * A 16-bit signed integer that defines the height, in logical coordinates, of the
         * ellipse used to draw the rounded corners.
         */
        protected int height;

        /**
         * A 16-bit signed integer that defines the width, in logical coordinates, of the
         * ellipse used to draw the rounded corners.
         */
        protected int width;

        protected final Rectangle2D bounds = new Rectangle2D.Double();


        @Override
        public HwmfRecordType getWmfRecordType() {
            return HwmfRecordType.roundRect;
        }

        @Override
        public int init(LittleEndianInputStream leis, long recordSize, int recordFunction) throws IOException {
            height = leis.readShort();
            width = leis.readShort();
            return 2*LittleEndianConsts.SHORT_SIZE+readBounds(leis, bounds);
        }

        @Override
        public void draw(HwmfGraphics ctx) {
            ctx.fill(getShape());
        }

        protected RoundRectangle2D getShape() {
            return new RoundRectangle2D.Double(bounds.getX(), bounds.getY(), bounds.getWidth(), bounds.getHeight(), width, height);
        }
    }


    /**
     * The META_ARC record draws an elliptical arc.
     */
    public static class WmfArc implements HwmfRecord {
        /** starting point of the arc */
        protected final Point2D startPoint = new Point2D.Double();

        /** ending point of the arc */
        protected final Point2D endPoint = new Point2D.Double();

        /** the bounding rectangle */
        protected final Rectangle2D bounds = new Rectangle2D.Double();


        @Override
        public HwmfRecordType getWmfRecordType() {
            return HwmfRecordType.arc;
        }

        @Override
        public int init(LittleEndianInputStream leis, long recordSize, int recordFunction) throws IOException {
            readPointS(leis, endPoint);
            readPointS(leis, startPoint);
            readBounds(leis, bounds);

            return 8*LittleEndianConsts.SHORT_SIZE;
        }

        @Override
        public void draw(HwmfGraphics ctx) {
            Shape s = getShape();
            switch (getFillDrawStyle()) {
                case FILL:
                    ctx.fill(s);
                    break;
                case DRAW:
                    ctx.draw(s);
                    break;
                case FILL_DRAW:
                    ctx.fill(s);
                    ctx.draw(s);
                    break;
            }
        }

        protected FillDrawStyle getFillDrawStyle() {
            switch (getWmfRecordType()) {
                default:
                case arc:
                    return FillDrawStyle.DRAW;
                case chord:
                case pie:
                    return FillDrawStyle.FILL_DRAW;
            }
        }

        protected Arc2D getShape() {
            double startAngle = Math.toDegrees(Math.atan2(-(startPoint.getY() - bounds.getCenterY()), startPoint.getX() - bounds.getCenterX()));
            double endAngle =   Math.toDegrees(Math.atan2(-(endPoint.getY() - bounds.getCenterY()), endPoint.getX() - bounds.getCenterX()));
            double arcAngle = (endAngle - startAngle) + (endAngle - startAngle > 0 ? 0 : 360);
            if (startAngle < 0) {
                startAngle += 360;
            }

            int arcClosure;
            switch (getWmfRecordType()) {
                default:
                case arc:
                    arcClosure = Arc2D.OPEN;
                    break;
                case chord:
                    arcClosure = Arc2D.CHORD;
                    break;
                case pie:
                    arcClosure = Arc2D.PIE;
                    break;
            }

            return new Arc2D.Double(bounds.getX(), bounds.getY(), bounds.getWidth(), bounds.getHeight(), startAngle, arcAngle, arcClosure);
        }

        @Override
        public String toString() {
            Arc2D arc = getShape();
            return
                "{ startPoint: "+pointToString(startPoint)+
                ", endPoint: "+pointToString(endPoint)+
                ", startAngle: "+arc.getAngleStart()+
                ", extentAngle: "+arc.getAngleExtent()+
                ", bounds: "+boundsToString(bounds)+
                " }";
        }
    }

    /**
     * The META_PIE record draws a pie-shaped wedge bounded by the intersection of an ellipse and two
     * radials. The pie is outlined by using the pen and filled by using the brush that are defined in the
     * playback device context.
     */
    public static class WmfPie extends WmfArc {

        @Override
        public HwmfRecordType getWmfRecordType() {
            return HwmfRecordType.pie;
        }
    }

    /**
     * The META_CHORD record draws a chord, which is defined by a region bounded by the intersection of
     * an ellipse with a line segment. The chord is outlined using the pen and filled using the brush
     * that are defined in the playback device context.
     */
    public static class WmfChord extends WmfArc {

        @Override
        public HwmfRecordType getWmfRecordType() {
            return HwmfRecordType.chord;
        }
    }


    /**
     * The META_SELECTOBJECT record specifies a graphics object for the playback device context. The
     * new object replaces the previous object of the same type, unless if the previous object is a palette
     * object. If the previous object is a palette object, then the META_SELECTPALETTE record must be
     * used instead of the META_SELECTOBJECT record, as the META_SELECTOBJECT record does not
     * support replacing the palette object type.
     */
    public static class WmfSelectObject implements HwmfRecord {

        /**
         * A 16-bit unsigned integer used to index into the WMF Object Table to
         * get the object to be selected.
         */
        protected int objectIndex;

        @Override
        public HwmfRecordType getWmfRecordType() {
            return HwmfRecordType.selectObject;
        }

        @Override
        public int init(LittleEndianInputStream leis, long recordSize, int recordFunction) throws IOException {
            objectIndex = leis.readUShort();
            return LittleEndianConsts.SHORT_SIZE;
        }

        @Override
        public void draw(HwmfGraphics ctx) {
            ctx.applyObjectTableEntry(objectIndex);
        }

        @Override
        public String toString() {
            return "{ index: "+objectIndex +" }";
        }
    }

    static int readBounds(LittleEndianInputStream leis, Rectangle2D bounds) {
        /**
         * The 16-bit signed integers that defines the corners of the bounding rectangle.
         */
        int bottom = leis.readShort();
        int right = leis.readShort();
        int top = leis.readShort();
        int left = leis.readShort();

        int x = Math.min(left, right);
        int y = Math.min(top, bottom);
        int w = Math.abs(left - right - 1);
        int h = Math.abs(top - bottom - 1);

        bounds.setRect(x, y, w, h);

        return 4 * LittleEndianConsts.SHORT_SIZE;
    }

    static int readRectS(LittleEndianInputStream leis, Rectangle2D bounds) {
        /**
         * The 16-bit signed integers that defines the corners of the bounding rectangle.
         */
        int left = leis.readShort();
        int top = leis.readShort();
        int right = leis.readShort();
        int bottom = leis.readShort();

        int x = Math.min(left, right);
        int y = Math.min(top, bottom);
        int w = Math.abs(left - right - 1);
        int h = Math.abs(top - bottom - 1);

        bounds.setRect(x, y, w, h);

        return 4 * LittleEndianConsts.SHORT_SIZE;
    }

    static int readPointS(LittleEndianInputStream leis, Point2D point) {
        /** a signed integer that defines the x/y-coordinate, in logical units. */
        int y = leis.readShort();
        int x = leis.readShort();
        point.setLocation(x, y);
        return 2*LittleEndianConsts.SHORT_SIZE;
    }

    static String polyToString(Path2D poly) {
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        final PathIterator iter = poly.getPathIterator(null);
        double[] pnts = new double[6];
        while (!iter.isDone()) {
            int segType = iter.currentSegment(pnts);
            switch (segType) {
                case PathIterator.SEG_MOVETO:
                    sb.append("{ type: 'move', x: "+pnts[0]+", y: "+pnts[1]+" }, ");
                    break;
                case PathIterator.SEG_LINETO:
                    sb.append("{ type: 'lineto', x: "+pnts[0]+", y: "+pnts[1]+" }, ");
                    break;
                case PathIterator.SEG_QUADTO:
                    sb.append("{ type: 'quad', x1: "+pnts[0]+", y1: "+pnts[1]+", x2: "+pnts[2]+", y2: "+pnts[3]+" }, ");
                    break;
                case PathIterator.SEG_CUBICTO:
                    sb.append("{ type: 'cubic', x1: "+pnts[0]+", y1: "+pnts[1]+", x2: "+pnts[2]+", y2: "+pnts[3]+", x3: "+pnts[4]+", y3: "+pnts[5]+" }, ");
                    break;
                case PathIterator.SEG_CLOSE:
                    sb.append("{ type: 'close' }, ");
                    break;
            }
            iter.next();
        }
        sb.append("]");
        return sb.toString();
    }

    @Internal
    public static String pointToString(Point2D point) {
        return "{ x: "+point.getX()+", y: "+point.getY()+" }";
    }

    @Internal
    public static String boundsToString(Rectangle2D bounds) {
        return "{ x: "+bounds.getX()+", y: "+bounds.getY()+", w: "+bounds.getWidth()+", h: "+bounds.getHeight()+" }";
    }

    @Internal
    public static String dimToString(Dimension2D dim) {
        return "{ w: "+dim.getWidth()+", h: "+dim.getHeight()+" }";
    }

    @Internal
    public static Rectangle2D normalizeBounds(Rectangle2D bounds) {
        return (bounds.getWidth() >= 0 && bounds.getHeight() >= 0) ? bounds
                : new Rectangle2D.Double(
                bounds.getWidth() >= 0 ? bounds.getMinX() : bounds.getMaxX(),
                bounds.getHeight() >= 0 ? bounds.getMinY() : bounds.getMaxY(),
                Math.abs(bounds.getWidth()),
                Math.abs(bounds.getHeight())
        );
    }

}
