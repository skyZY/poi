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

package org.apache.poi.hslf.model;

import org.apache.poi.ddf.*;
import org.apache.poi.hslf.usermodel.*;
import org.apache.poi.sl.usermodel.ShapeContainer;
import org.apache.poi.sl.usermodel.ShapeType;

/**
 * Represents a line in a PowerPoint drawing
 *
 *  @author Yegor Kozlov
 */
public final class Line extends HSLFSimpleShape {
    public Line(EscherContainerRecord escherRecord, ShapeContainer<HSLFShape> parent){
        super(escherRecord, parent);
    }

    public Line(ShapeContainer<HSLFShape> parent){
        super(null, parent);
        _escherContainer = createSpContainer(parent instanceof HSLFGroupShape);
    }

    public Line(){
        this(null);
    }

    protected EscherContainerRecord createSpContainer(boolean isChild){
        _escherContainer = super.createSpContainer(isChild);
        
        setShapeType(ShapeType.LINE);

        EscherSpRecord spRecord = _escherContainer.getChildById(EscherSpRecord.RECORD_ID);
        short type = (short)((ShapeType.LINE.nativeId << 4) | 0x2);
        spRecord.setOptions(type);

        //set default properties for a line
        EscherOptRecord opt = getEscherOptRecord();

        //default line properties
        setEscherProperty(opt, EscherProperties.GEOMETRY__SHAPEPATH, 4);
        setEscherProperty(opt, EscherProperties.GEOMETRY__FILLOK, 0x10000);
        setEscherProperty(opt, EscherProperties.FILL__NOFILLHITTEST, 0x100000);
        setEscherProperty(opt, EscherProperties.LINESTYLE__COLOR, 0x8000001);
        setEscherProperty(opt, EscherProperties.LINESTYLE__NOLINEDRAWDASH, 0xA0008);
        setEscherProperty(opt, EscherProperties.SHADOWSTYLE__COLOR, 0x8000002);

        return _escherContainer;
    }
    
    /**
     * Sets the orientation of the line, if inverse is false, then line goes
     * from top-left to bottom-right, otherwise use inverse equals true 
     *
     * @param inverse the orientation of the line
     */
    public void setInverse(boolean inverse) {
        setShapeType(inverse ? ShapeType.LINE_INV : ShapeType.LINE);
    }
    
    /**
     * Gets the orientation of the line, if inverse is false, then line goes
     * from top-left to bottom-right, otherwise inverse equals true 
     *
     * @return inverse the orientation of the line
     */
    public boolean isInverse() {
        return (getShapeType() == ShapeType.LINE_INV);
    }
}
