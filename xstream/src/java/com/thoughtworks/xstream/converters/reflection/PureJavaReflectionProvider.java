/*
 * Copyright (C) 2004, 2005, 2006 Joe Walnes.
 * Copyright (C) 2006, 2007, 2009, 2011, 2013, 2016, 2018, 2020, 2021 XStream Committers.
 * All rights reserved.
 *
 * The software in this package is published under the terms of the BSD
 * style license a copy of which has been included with this distribution in
 * the LICENSE.txt file.
 * 
 * Created on 07. March 2004 by Joe Walnes
 */
package com.thoughtworks.xstream.converters.reflection;

import com.thoughtworks.xstream.converters.ConversionException;
import com.thoughtworks.xstream.converters.ErrorWritingException;
import com.thoughtworks.xstream.core.JVM;
import com.thoughtworks.xstream.core.util.Fields;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectStreamClass;
import java.io.ObjectStreamConstants;
import java.io.Serializable;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Iterator;
import java.util.Map;
import java.util.WeakHashMap;

import com.thoughtworks.xstream.converters.ConversionException;
import com.thoughtworks.xstream.converters.ErrorWritingException;
import com.thoughtworks.xstream.core.util.Fields;


/**
 * Pure Java ObjectFactory that instantiates objects using standard Java reflection, however the types of objects that
 * can be constructed are limited.
 * <p>
 * Can newInstance: classes with public visibility, outer classes, static inner classes, classes with default
 * constructors and any class that implements java.io.Serializable.
 * </p>
 * <p>
 * Cannot newInstance: classes without public visibility, non-static inner classes, classes without default
 * constructors. Note that any code in the constructor of a class will be executed when the ObjectFactory instantiates
 * the object.
 * </p>
 *
 * @author Joe Walnes
 */
public class PureJavaReflectionProvider implements ReflectionProvider {

    private transient Map objectStreamClassCache;
    private transient Map serializedDataCache;
    protected FieldDictionary fieldDictionary;

    public PureJavaReflectionProvider() {
        this(new FieldDictionary(new ImmutableFieldKeySorter()));
    }

    public PureJavaReflectionProvider(FieldDictionary fieldDictionary) {
        this.fieldDictionary = fieldDictionary;
        init();
    }

    public Object newInstance(Class type) {
        ErrorWritingException ex = null;
        if (type == void.class || type == Void.class) {
            ex = new ConversionException("Security alert: Marshalling rejected");
        } else {
            try {
                Constructor[] constructors = type.getDeclaredConstructors();
                for (int i = 0; i < constructors.length; i++) {
                    final Constructor constructor = constructors[i];
                    if (constructor.getParameterTypes().length == 0) {
                        if (!constructor.isAccessible()) {
                            constructor.setAccessible(true);
                        }
                        return constructor.newInstance(new Object[0]);
                    }
                }
                if (Serializable.class.isAssignableFrom(type)) {
                    return instantiateUsingSerialization(type);
                } else {
                    ex = new ObjectAccessException("Cannot construct type as it does not have a no-args constructor");
                }
            } catch (InstantiationException e) {
                ex = new ObjectAccessException("Cannot construct type", e);
            } catch (IllegalAccessException e) {
                ex = new ObjectAccessException("Cannot construct type", e);
            } catch (InvocationTargetException e) {
                if (e.getTargetException() instanceof RuntimeException) {
                    throw (RuntimeException)e.getTargetException();
                } else if (e.getTargetException() instanceof Error) {
                    throw (Error)e.getTargetException();
                } else {
                    ex = new ObjectAccessException("Constructor for type threw an exception", e.getTargetException());
                }
            }
        }
        ex.add("construction-type", type.getName());
        throw ex;
    }

    private Object instantiateUsingSerialization(final Class type) {
        ObjectAccessException oaex = null;
        try {
            if (Reflections.newInstance != null) {
                synchronized (objectStreamClassCache) {
                    ObjectStreamClass osClass = (ObjectStreamClass)objectStreamClassCache.get(type);
                    if (osClass == null) {
                        osClass = ObjectStreamClass.lookup(type);
                        objectStreamClassCache.put(type, osClass);
                    }
                    return Reflections.newInstance.invoke(osClass, new Object[0]);
                }
            }
            byte[] data;
            synchronized (serializedDataCache) {
                data = (byte[])serializedDataCache.get(type);
                if (data == null) {
                    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                    DataOutputStream stream = new DataOutputStream(bytes);
                    stream.writeShort(ObjectStreamConstants.STREAM_MAGIC);
                    stream.writeShort(ObjectStreamConstants.STREAM_VERSION);
                    stream.writeByte(ObjectStreamConstants.TC_OBJECT);
                    stream.writeByte(ObjectStreamConstants.TC_CLASSDESC);
                    stream.writeUTF(type.getName());
                    stream.writeLong(ObjectStreamClass.lookup(type).getSerialVersionUID());
                    stream.writeByte(2); // classDescFlags (2 = Serializable)
                    stream.writeShort(0); // field count
                    stream.writeByte(ObjectStreamConstants.TC_ENDBLOCKDATA);
                    stream.writeByte(ObjectStreamConstants.TC_NULL);
                    data = bytes.toByteArray();
                    serializedDataCache.put(type, data);
                }
            }
            final ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(data)) {
                protected Class resolveClass(final ObjectStreamClass desc) throws ClassNotFoundException {
                    return Class.forName(desc.getName(), false, type.getClassLoader());
                }
            };
            return in.readObject();
        } catch (final ObjectAccessException e) {
            oaex = e;
        } catch (final IOException e) {
            oaex = new ObjectAccessException("Cannot create type by JDK serialization", e);
        } catch (ClassNotFoundException e) {
            oaex = new ObjectAccessException("Cannot find class", e);
        } catch (IllegalAccessException e) {
            oaex = new ObjectAccessException("Cannot create type by JDK object stream data", e);
        } catch (IllegalArgumentException e) {
            oaex = new ObjectAccessException("Cannot create type by JDK object stream data", e);
        } catch (InvocationTargetException e) {
            oaex = new ObjectAccessException("Cannot create type by JDK object stream data", e);
        }
        oaex.add("construction-type", type.getName());
        throw oaex;
    }

    public void visitSerializableFields(Object object, ReflectionProvider.Visitor visitor) {
        for (Iterator iterator = fieldDictionary.fieldsFor(object.getClass()); iterator.hasNext();) {
            Field field = (Field)iterator.next();
            if (!fieldModifiersSupported(field)) {
                continue;
            }
            validateFieldAccess(field);
            Object value = Fields.read(field, object);
            visitor.visit(field.getName(), field.getType(), field.getDeclaringClass(), value);
        }
    }

    public void writeField(Object object, String fieldName, Object value, Class definedIn) {
        Field field = fieldDictionary.field(object.getClass(), fieldName, definedIn);
        validateFieldAccess(field);
        Fields.write(field, object, value);
    }

    public Class getFieldType(Object object, String fieldName, Class definedIn) {
        return fieldDictionary.field(object.getClass(), fieldName, definedIn).getType();
    }

    /**
     * @deprecated As of 1.4.5, use {@link #getFieldOrNull(Class, String)} instead
     */
    public boolean fieldDefinedInClass(String fieldName, Class type) {
        Field field = fieldDictionary.fieldOrNull(type, fieldName, null);
        return field != null && fieldModifiersSupported(field);
    }

    protected boolean fieldModifiersSupported(Field field) {
        int modifiers = field.getModifiers();
        return !(Modifier.isStatic(modifiers) || Modifier.isTransient(modifiers));
    }

    protected void validateFieldAccess(Field field) {
        if (Modifier.isFinal(field.getModifiers())) {
            if (JVM.isVersion(5)) {
                if (!field.isAccessible()) {
                    field.setAccessible(true);
                }
            } else {
                throw new ObjectAccessException("Invalid final field "
                    + field.getDeclaringClass().getName()
                    + "."
                    + field.getName());
            }
        }
    }

    public Field getField(Class definedIn, String fieldName) {
        return fieldDictionary.field(definedIn, fieldName, null);
    }

    public Field getFieldOrNull(Class definedIn, String fieldName) {
        return fieldDictionary.fieldOrNull(definedIn, fieldName, null);
    }

    public void setFieldDictionary(FieldDictionary dictionary) {
        this.fieldDictionary = dictionary;
    }

    private Object readResolve() {
        init();
        return this;
    }

    protected void init() {
        objectStreamClassCache = new WeakHashMap();
        serializedDataCache = new WeakHashMap();
    }

    private static class Reflections {
        private final static Method newInstance;
        static {
            Method method = null;
            try {
                method = ObjectStreamClass.class.getDeclaredMethod("newInstance", new Class[0]);
                method.setAccessible(true);
            } catch (final NoSuchMethodException e) {
                // not available
            } catch (final SecurityException e) {
                // not available
            }
            newInstance = method;
        }
    }
}
