/*
 * Copyright (C) 2004, 2005, 2006 Joe Walnes.
 * Copyright (C) 2006, 2007, 2008, 2010, 2013, 2017, 2021 XStream Committers.
 * All rights reserved.
 *
 * The software in this package is published under the terms of the BSD
 * style license a copy of which has been included with this distribution in
 * the LICENSE.txt file.
 * 
 * Created on 07. March 2004 by Joe Walnes
 */
package com.thoughtworks.xstream.converters.reflection;

import com.thoughtworks.acceptance.objects.StandardObject;
import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.converters.Converter;
import com.thoughtworks.xstream.core.ClassLoaderReference;
import com.thoughtworks.xstream.core.util.CompositeClassLoader;
import com.thoughtworks.xstream.io.xml.XppDriver;
import com.thoughtworks.xstream.mapper.AttributeMapper;
import com.thoughtworks.xstream.mapper.DefaultMapper;
import com.thoughtworks.xstream.mapper.Mapper;

import junit.framework.TestCase;

public class ReflectionConverterTest extends TestCase {

    public static class World extends StandardObject {
        int anInt = 1;
        Integer anInteger = new Integer(2);
        char anChar = 'a';
        Character anCharacter = new Character('w');
        boolean anBool = true;
        Boolean anBoolean = new Boolean(false);
        byte aByte = 4;
        Byte aByteClass = new Byte("5");
        short aShort = 6;
        Short aShortClass = new Short("7");
        float aFloat = 8f;
        Float aFloatClass = new Float("9");
        long aLong = 10;
        Long aLongClass = new Long("11");
        String anString = new String("XStream programming!");
    }

    public void testSerializesAllPrimitiveFieldsInACustomObject() {
        World world = new World();

        XStream xstream = createXStream();
        xstream.alias("world", World.class);

        String expected =
                "<world>\n" +
                "  <anInt>1</anInt>\n" +
                "  <anInteger>2</anInteger>\n" +
                "  <anChar>a</anChar>\n" +
                "  <anCharacter>w</anCharacter>\n" +
                "  <anBool>true</anBool>\n" +
                "  <anBoolean>false</anBoolean>\n" +
                "  <aByte>4</aByte>\n" +
                "  <aByteClass>5</aByteClass>\n" +
                "  <aShort>6</aShort>\n" +
                "  <aShortClass>7</aShortClass>\n" +
                "  <aFloat>8.0</aFloat>\n" +
                "  <aFloatClass>9.0</aFloatClass>\n" +
                "  <aLong>10</aLong>\n" +
                "  <aLongClass>11</aLongClass>\n" +
                "  <anString>XStream programming!</anString>\n" +
                "</world>";

        assertEquals(expected, xstream.toXML(world));
    }

    private XStream createXStream() {
        XStream xstream = new XStream(new XppDriver());
        return xstream;
    }

    public static class TypesOfFields extends StandardObject {
        String normal = "normal";
        transient String trans = "transient";
        static String stat = "stat";
    }

    public void testDoesNotSerializeTransientOrStaticFields() {
        TypesOfFields fields = new TypesOfFields();
        String expected = "" +
                "<types>\n" +
                "  <normal>normal</normal>\n" +
                "</types>";

        XStream xstream = createXStream();
        xstream.alias("types", TypesOfFields.class);

        String xml = xstream.toXML(fields);
        assertEquals(expected, xml);

    }
    
    public void testCanBeOverloadedToDeserializeTransientFields() {
        XStream xstream = createXStream();
        xstream.allowTypesByWildcard(new String[] {getClass().getName()+"$*"});
        xstream.alias("types", TypesOfFields.class);
        xstream.registerConverter(new ReflectionConverter(xstream.getMapper(), xstream
            .getReflectionProvider()) {

            public boolean canConvert(Class type) {
                return type == TypesOfFields.class;
            }

            protected boolean shouldUnmarshalTransientFields() {
                return true;
            }
        });

        String xml = ""
            + "<types>\n"
            + "  <normal>normal</normal>\n"
            + "  <trans>foo</trans>\n"
            + "</types>";

        TypesOfFields fields = (TypesOfFields)xstream.fromXML(xml);
        assertEquals("foo", fields.trans);
    }

    public void testCustomConverterCanBeInstantiatedAndRegisteredWithDesiredPriority() {
        XStream xstream = createXStream();
        // using default mapper instead of XStream#buildMapper()
        Mapper mapper = new DefaultMapper(new ClassLoaderReference(new CompositeClassLoader()));
        // AttributeMapper required by ReflectionConverter
        mapper = new AttributeMapper(mapper, xstream.getConverterLookup(), xstream.getReflectionProvider());
        Converter converter = new CustomReflectionConverter(mapper, new PureJavaReflectionProvider());
        xstream.registerConverter(converter, -20);
        xstream.alias("world", World.class);
        World world = new World();

        String expected =
                "<world>\n" +
                "  <anInt class=\"java.lang.Integer\">1</anInt>\n" +
                "  <anInteger>2</anInteger>\n" +
                "  <anChar class=\"java.lang.Character\">a</anChar>\n" +
                "  <anCharacter>w</anCharacter>\n" +
                "  <anBool class=\"java.lang.Boolean\">true</anBool>\n" +
                "  <anBoolean>false</anBoolean>\n" +
                "  <aByte class=\"java.lang.Byte\">4</aByte>\n" +
                "  <aByteClass>5</aByteClass>\n" +
                "  <aShort class=\"java.lang.Short\">6</aShort>\n" +
                "  <aShortClass>7</aShortClass>\n" +
                "  <aFloat class=\"java.lang.Float\">8.0</aFloat>\n" +
                "  <aFloatClass>9.0</aFloatClass>\n" +
                "  <aLong class=\"java.lang.Long\">10</aLong>\n" +
                "  <aLongClass>11</aLongClass>\n" +
                "  <anString>XStream programming!</anString>\n" +
                "</world>";
        assertEquals(expected, xstream.toXML(world));

    }

    static class CustomReflectionConverter extends ReflectionConverter {

        public CustomReflectionConverter(Mapper mapper, ReflectionProvider reflectionProvider) {
            super(mapper, reflectionProvider);
        }
    }
    
}
