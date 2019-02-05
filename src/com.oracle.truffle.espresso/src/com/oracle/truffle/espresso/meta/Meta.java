/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.truffle.espresso.meta;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.function.Predicate;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.espresso.descriptors.ByteString.Signature;
import com.oracle.truffle.espresso.descriptors.Types;
import com.oracle.truffle.espresso.impl.ArrayKlass;
import com.oracle.truffle.espresso.descriptors.ByteString;
import com.oracle.truffle.espresso.descriptors.ByteString.Name;
import com.oracle.truffle.espresso.descriptors.ByteString.Type;
import com.oracle.truffle.espresso.impl.ContextAccess;
import com.oracle.truffle.espresso.impl.Field;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.impl.ObjectKlass;
import com.oracle.truffle.espresso.impl.PrimitiveKlass;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.EspressoException;
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.espresso.runtime.StaticObjectArray;
import com.oracle.truffle.espresso.substitutions.Host;

/**
 * Introspection API to access the guest world from the host. Provides seamless conversions from
 * host to guest classes for a well known subset (e.g. common types and exceptions).
 */
public final class Meta implements ContextAccess {

    private final EspressoContext context;

    public Meta(EspressoContext context) {
        CompilerAsserts.neverPartOfCompilation();
        this.context = context;

        // Give access to the partially-built Meta instance.
        context.setBootstrapMeta(this);

        // Core types.
        Object = knownKlass(Type.Object);
        Cloneable = knownKlass(Type.Cloneable);
        Serializable = knownKlass(Type.Serializable);
        ARRAY_SUPERINTERFACES = new ObjectKlass[]{Cloneable, Serializable};

        Object_array = Object.array();

        String = knownKlass(Type.String);
        Class = knownKlass(Type.Class);
        Class_forName_String = Class.lookupDeclaredMethod(Name.forName, Signature.Class_String);

        // Primitives.
        _boolean = knownPrimitive(Type._boolean);
        _byte = knownPrimitive(Type._byte);
        _char = knownPrimitive(Type._char);
        _short = knownPrimitive(Type._short);
        _float = knownPrimitive(Type._float);
        _int = knownPrimitive(Type._int);
        _double = knownPrimitive(Type._double);
        _long = knownPrimitive(Type._long);
        _void = knownPrimitive(Type._void);

        _boolean_array = _boolean.array();
        _byte_array = _byte.array();
        _char_array = _char.array();
        _short_array = _short.array();
        _float_array = _float.array();
        _int_array = _int.array();
        _double_array = _double.array();
        _long_array = _long.array();

        // Boxed types.
        Boolean = knownKlass(Type.Boolean);
        Byte = knownKlass(Type.Byte);
        Character = knownKlass(Type.Character);
        Short = knownKlass(Type.Short);
        Float = knownKlass(Type.Float);
        Integer = knownKlass(Type.Integer);
        Double = knownKlass(Type.Double);
        Long = knownKlass(Type.Long);
        Void = knownKlass(Type.Void);

        Boolean_valueOf = Boolean.lookupDeclaredMethod(Name.valueOf, Signature.Boolean_boolean);
        Byte_valueOf = Byte.lookupDeclaredMethod(Name.valueOf, Signature.Byte_byte);
        Character_valueOf = Character.lookupDeclaredMethod(Name.valueOf, Signature.Character_char);
        Short_valueOf = Short.lookupDeclaredMethod(Name.valueOf, Signature.Short_short);
        Float_valueOf = Float.lookupDeclaredMethod(Name.valueOf, Signature.Float_float);
        Integer_valueOf = Integer.lookupDeclaredMethod(Name.valueOf, Signature.Integer_int);
        Double_valueOf = Double.lookupDeclaredMethod(Name.valueOf, Signature.Double_double);
        Long_valueOf = Long.lookupDeclaredMethod(Name.valueOf, Signature.Long_long);

        String_value = String.lookupDeclaredField(Name.value, Type._char_array);
        String_hash = String.lookupDeclaredField(Name.hash, Type._int);
        String_hashCode = String.lookupDeclaredMethod(Name.hashCode, Signature._int);
        String_length = String.lookupDeclaredMethod(Name.length, Signature._int);

        Throwable = knownKlass(Type.Throwable);
        Throwable_backtrace = Throwable.lookupField(Name.backtrace, Type.Object);

        StackTraceElement = knownKlass(Type.StackTraceElement);
        StackTraceElement_init = StackTraceElement.lookupDeclaredMethod(Name.INIT, Signature._void_String_String_String_int);

        ClassNotFoundException = knownKlass(Type.ClassNotFoundException);
        StackOverflowError = knownKlass(Type.StackOverflowError);
        OutOfMemoryError = knownKlass(Type.OutOfMemoryError);

        PrivilegedActionException = knownKlass(Type.PrivilegedActionException);
        PrivilegedActionException_init_Exception = PrivilegedActionException.lookupDeclaredMethod(Name.INIT, Signature._void_Exception);


        ClassLoader = knownKlass(Type.ClassLoader);
        ClassLoader_findNative = ClassLoader.lookupDeclaredMethod(Name.findNative, Signature._long_ClassLoader_String);
        ClassLoader_getSystemClassLoader = ClassLoader.lookupDeclaredMethod(Name.getSystemClassLoader, Signature.ClassLoader);

        // Guest reflection.
        Constructor = knownKlass(Type.Constructor);
        Constructor_clazz = Constructor.lookupDeclaredField(Name.clazz, Class.getType());
        Constructor_root = Constructor.lookupDeclaredField(Name.root, Constructor.getType());

        Method = knownKlass(Type.Method);
        Method_root = Method.lookupDeclaredField(Name.root, Method.getType());

        Field = knownKlass(Type.Field);
        Field_root = Field.lookupDeclaredField(Name.root, Field.getType());

        ByteBuffer = knownKlass(Type.ByteBuffer);
        ByteBuffer_wrap = ByteBuffer.lookupDeclaredMethod(Name.wrap, Signature.ByteBuffer_byte_array);

        Thread = knownKlass(Type.Thread);
        ThreadGroup = knownKlass(Type.ThreadGroup);

        Thread_group = Thread.lookupDeclaredField(Name.group, ThreadGroup.getType());
        Thread_name = Thread.lookupDeclaredField(Name.name, String.getType());
        Thread_priority = Thread.lookupDeclaredField(Name.priority, _int.getType());
        Thread_blockerLock = Thread.lookupDeclaredField(Name.blockerLock, Object.getType());

        System = knownKlass(Type.System);
        System_initializeSystemClass = System.lookupDeclaredMethod(Name.initializeSystemClass, Signature._void);
        System_exit = System.lookupDeclaredMethod(Name.exit, Signature._void_int);
    }

    public final ObjectKlass Object;
    public final ArrayKlass Object_array;

    public final ObjectKlass String;
    public final ObjectKlass Class;
    public final Method Class_forName_String;

    // Primitives.
    public final PrimitiveKlass _boolean;
    public final PrimitiveKlass _byte;
    public final PrimitiveKlass _char;
    public final PrimitiveKlass _short;
    public final PrimitiveKlass _float;
    public final PrimitiveKlass _int;
    public final PrimitiveKlass _double;
    public final PrimitiveKlass _long;
    public final PrimitiveKlass _void;

    public final ArrayKlass _boolean_array;
    public final ArrayKlass _byte_array;
    public final ArrayKlass _char_array;
    public final ArrayKlass _short_array;
    public final ArrayKlass _float_array;
    public final ArrayKlass _int_array;
    public final ArrayKlass _double_array;
    public final ArrayKlass _long_array;

    // Boxed primitives.
    public final ObjectKlass Boolean;
    public final ObjectKlass Byte;
    public final ObjectKlass Character;
    public final ObjectKlass Short;
    public final ObjectKlass Integer;
    public final ObjectKlass Float;
    public final ObjectKlass Double;
    public final ObjectKlass Long;
    public final ObjectKlass Void;

    // Boxing conversions.
    public final Method Boolean_valueOf;
    public final Method Byte_valueOf;
    public final Method Character_valueOf;
    public final Method Short_valueOf;
    public final Method Float_valueOf;
    public final Method Integer_valueOf;
    public final Method Double_valueOf;
    public final Method Long_valueOf;

    // Guest String.
    public final Field String_value;
    public final Field String_hash;
    public final Method String_hashCode;
    public final Method String_length;

    public final ObjectKlass ClassLoader;
    public final Method ClassLoader_findNative;
    public final Method ClassLoader_getSystemClassLoader;

    public final ObjectKlass Constructor;
    public final Field Constructor_clazz;
    public final Field Constructor_root;

    public final ObjectKlass Method;
    public final Field Method_root;

    public final ObjectKlass Field;
    public final Field Field_root;

    public final ObjectKlass ClassNotFoundException;
    public final ObjectKlass StackOverflowError;
    public final ObjectKlass OutOfMemoryError;
    public final ObjectKlass Throwable;
    public final Field Throwable_backtrace;

    public final ObjectKlass StackTraceElement;
    public final Method StackTraceElement_init;

    public final ObjectKlass PrivilegedActionException;
    public final Method PrivilegedActionException_init_Exception;

    // Array support.
    public final ObjectKlass Cloneable;
    public final ObjectKlass Serializable;

    public final ObjectKlass ByteBuffer;
    public final Method ByteBuffer_wrap;

    public final ObjectKlass ThreadGroup;
    public final ObjectKlass Thread;
    public final Field Thread_group;
    public final Field Thread_name;
    public final Field Thread_priority;
    public final Field Thread_blockerLock;

    public final ObjectKlass System;
    public final Method System_initializeSystemClass;
    public final Method System_exit;

    @CompilationFinal(dimensions = 1) //
    public final ObjectKlass[] ARRAY_SUPERINTERFACES;

    private static boolean isKnownClass(java.lang.Class<?> clazz) {
        // Cheap check: (host) known classes are loaded by the BCL.
        return clazz.getClassLoader() == null;
    }

    public StaticObject initEx(java.lang.Class<?> clazz) {
        assert Throwable.class.isAssignableFrom(clazz);
        Klass exKlass = throwableKlass(clazz);
        StaticObject ex = exKlass.allocateInstance();
        exKlass.lookupDeclaredMethod(Name.INIT, Signature._void).invokeDirect(ex);
        return ex;
    }

    public static StaticObject initEx(Klass klass, String message) {
        StaticObject ex = klass.allocateInstance();
        // Call constructor.
        klass.lookupDeclaredMethod(Name.INIT, Signature._void_String).invokeDirect(ex, ex.getKlass().getMeta().toGuestString(message));
        return ex;
    }

    public StaticObject initEx(java.lang.Class<?> clazz, String message) {
        assert Throwable.class.isAssignableFrom(clazz);
        Klass exKlass = throwableKlass(clazz);
        StaticObject ex = exKlass.allocateInstance();
        exKlass.lookupDeclaredMethod(Name.INIT, Signature._void_String).invokeDirect(ex, toGuestString(message));
        return ex;
    }

    public StaticObject initEx(java.lang.Class<?> clazz, @Host(Throwable.class) StaticObject cause) {
        assert Throwable.class.isAssignableFrom(clazz);
        Klass exKlass = throwableKlass(clazz);
        StaticObject ex = exKlass.allocateInstance();
        exKlass.lookupDeclaredMethod(Name.INIT, Signature._void_Throwable).invokeDirect(ex, cause);
        return ex;
    }

    public EspressoException throwEx(java.lang.Class<?> clazz) {
        throw new EspressoException(initEx(clazz));
    }

    public EspressoException throwEx(java.lang.Class<?> clazz, String message) {
        throw new EspressoException(initEx(clazz, message));
    }

    public EspressoException throwEx(java.lang.Class<?> clazz, @Host(Throwable.class) StaticObject cause) {
        assert Throwable.isAssignableFrom(cause.getKlass());
        throw new EspressoException(initEx(clazz, cause));
    }

    @TruffleBoundary
    public Klass throwableKlass(java.lang.Class<?> exceptionClass) {
        assert isKnownClass(exceptionClass);
        assert Throwable.class.isAssignableFrom(exceptionClass);
        return knownKlass(exceptionClass);
    }

    public ObjectKlass knownKlass(ByteString<Type> type) {
        return (ObjectKlass) getRegistries().loadKlassWithBootClassLoader(type);
    }

    public ObjectKlass knownKlass(java.lang.Class<?> hostClass) {
        assert isKnownClass(hostClass);
        // Resolve non-primitive classes using BCL.
        return knownKlass(getTypes().fromClass(hostClass));
    }

    public PrimitiveKlass knownPrimitive(ByteString<Type> primitiveType) {
        assert Types.isPrimitive(primitiveType);
        // Resolve primitive classes using BCL.
        return (PrimitiveKlass) getRegistries().loadKlassWithBootClassLoader(primitiveType);
    }

    public PrimitiveKlass knownPrimitive(java.lang.Class<?> primitiveClass) {
        // assert isKnownClass(hostClass);
        assert primitiveClass.isPrimitive();
        // Resolve primitive classes using BCL.
        return knownPrimitive(getTypes().fromClass(primitiveClass));
    }

    @TruffleBoundary
    public Klass loadKlass(ByteString<Type> type, @Host(ClassLoader.class) StaticObject classLoader) {
        assert classLoader != null : "use StaticObject.NULL for BCL";
        return getRegistries().loadKlass(type, classLoader);
    }

    @TruffleBoundary
    public static String toHostString(StaticObject str) {
        if (StaticObject.isNull(str)) {
            return null;
        }
        Meta meta = str.getKlass().getMeta();
        char[] value = ((StaticObjectArray) meta.String_value.get(str)).unwrap();
        return HostJava.createString(value);
    }

    @TruffleBoundary
    public StaticObject toGuestString(String hostString) {
        if (hostString == null) {
            return StaticObject.NULL;
        }
        final char[] value = HostJava.getStringValue(hostString);
        final int hash = HostJava.getStringHash(hostString);
        StaticObject guestString = String.allocateInstance();
        String_value.set(guestString, StaticObjectArray.wrap(value));
        String_hash.set(guestString, hash);
        // String.hashCode must be equivalent for host and guest.
        assert hostString.hashCode() == (int) String_hashCode.invokeDirect(guestString);
        return guestString;
    }

    @TruffleBoundary
    public StaticObject toGuestString(ByteString<?> hostString) {
        if (hostString == null) {
            return StaticObject.NULL;
        }
        return toGuestString(hostString.toString());
    }

    public Object toGuestBoxed(Object hostObject) {
        if (hostObject == null) {
            return StaticObject.NULL;
        }
        if (hostObject instanceof String) {
            return toGuestString((String) hostObject);
        }
        if (hostObject instanceof StaticObject || (hostObject.getClass().isArray() && hostObject.getClass().getComponentType().isPrimitive())) {
            return hostObject;
        }

        if (Arrays.stream(JavaKind.values()).anyMatch(new Predicate<JavaKind>() {
            @Override
            public boolean test(JavaKind c) {
                return c.toBoxedJavaClass() == hostObject.getClass();
            }
        })) {
            // boxed value
            return hostObject;
        }

        throw EspressoError.shouldNotReachHere(hostObject + " cannot be converted to guest world");
    }

    public Object toHostBoxed(Object object) {
        assert object != null;
        if (object instanceof StaticObject) {
            StaticObject guestObject = (StaticObject) object;
            if (StaticObject.isNull(guestObject)) {
                return null;
            }
            if (guestObject == StaticObject.VOID) {
                return null;
            }
            if (guestObject instanceof StaticObjectArray) {
                return ((StaticObjectArray) guestObject).unwrap();
            }
            if (guestObject.getKlass() == String) {
                return toHostString(guestObject);
            }
        }
        return object;
    }

    @Override
    public EspressoContext getContext() {
        return context;
    }

    // region Low level host String access

    private static class HostJava {

        private static final java.lang.reflect.Field String_value;
        private static final java.lang.reflect.Field String_hash;
        private static final Constructor<String> String_init;

        static {
            try {
                String_value = String.class.getDeclaredField("value");
                String_value.setAccessible(true);
                String_hash = String.class.getDeclaredField("hash");
                String_hash.setAccessible(true);
                String_init = String.class.getDeclaredConstructor(char[].class, boolean.class);
                String_init.setAccessible(true);
            } catch (NoSuchMethodException | NoSuchFieldException e) {
                throw EspressoError.shouldNotReachHere(e);
            }
        }

        private static char[] getStringValue(String s) {
            try {
                return (char[]) String_value.get(s);
            } catch (IllegalAccessException e) {
                throw EspressoError.shouldNotReachHere(e);
            }
        }

        private static int getStringHash(String s) {
            try {
                return (int) String_hash.get(s);
            } catch (IllegalAccessException e) {
                throw EspressoError.shouldNotReachHere(e);
            }
        }

        private static String createString(final char[] value) {
            try {
                return HostJava.String_init.newInstance(value, true);
            } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
                throw EspressoError.shouldNotReachHere(e);
            }
        }
    }

    // endregion
}
