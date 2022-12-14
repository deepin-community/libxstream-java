/*
 * Copyright (C) 2005 Joe Walnes.
 * Copyright (C) 2006, 2007, 2013, 2017, 2021 XStream Committers.
 * All rights reserved.
 *
 * The software in this package is published under the terms of the BSD
 * style license a copy of which has been included with this distribution in
 * the LICENSE.txt file.
 * 
 * Created on 06. April 2005 by Joe Walnes
 */
package com.thoughtworks.xstream.converters.enums;

import com.thoughtworks.xstream.XStream;

import junit.framework.TestCase;

import java.util.EnumSet;

public class EnumSetConverterTest extends TestCase {

    private XStream xstream;

    protected void setUp() throws Exception {
        super.setUp();
        xstream = new XStream();
    }

    public void testPutsEnumsInCompactCommaSeparatedString() {
        xstream.alias("simple", SimpleEnum.class);
        EnumSet<SimpleEnum> set = EnumSet.of(SimpleEnum.GREEN, SimpleEnum.BLUE);

        String expectedXml = "<enum-set enum-type=\"simple\">GREEN,BLUE</enum-set>";

        assertEquals(expectedXml, xstream.toXML(set));
        assertEquals(set, xstream.fromXML(expectedXml));
    }

    public void testSupportsJumboEnumSetsForMoreThan64Elements() {
        xstream.alias("big", BigEnum.class);
        EnumSet<BigEnum> jumboSet = EnumSet.allOf(BigEnum.class);

        String expectedXml = "<enum-set enum-type=\"big\">" +
                "A1,B1,C1,D1,E1,F1,G1,H1,I1,J1,K1,L1,M1,N1,O1,P1,Q1,R1,S1,T1,U1,V1,W1,X1,Y1,Z1," +
                "A2,B2,C2,D2,E2,F2,G2,H2,I2,J2,K2,L2,M2,N2,O2,P2,Q2,R2,S2,T2,U2,V2,W2,X2,Y2,Z2," +
                "A3,B3,C3,D3,E3,F3,G3,H3,I3,J3,K3,L3,M3,N3,O3,P3,Q3,R3,S3,T3,U3,V3,W3,X3,Y3,Z3" +
                "</enum-set>";

        assertEquals(expectedXml, xstream.toXML(jumboSet));
        assertEquals(jumboSet, xstream.fromXML(expectedXml));
    }

    public void testSupportsPolymorphicEnums() {
        xstream.alias("poly", PolymorphicEnum.class);
        EnumSet<PolymorphicEnum> set = EnumSet.allOf(PolymorphicEnum.class);

        String expectedXml = "<enum-set enum-type=\"poly\">A,B,C</enum-set>";

        assertEquals(expectedXml, xstream.toXML(set));
        assertEquals(set, xstream.fromXML(expectedXml));
    }

    public void testEmptyEnumSet() {
        xstream.alias("simple", SimpleEnum.class);
        EnumSet<SimpleEnum> set = EnumSet.noneOf(SimpleEnum.class);

        String expectedXml = "<enum-set enum-type=\"simple\"></enum-set>";

        assertEquals(expectedXml, xstream.toXML(set));
        assertEquals(set, xstream.fromXML(expectedXml));
    }

}
