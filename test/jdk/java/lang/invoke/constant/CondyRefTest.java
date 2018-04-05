/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.invoke.constant.ClassRef;
import java.lang.invoke.constant.ConstantRef;
import java.lang.invoke.constant.DynamicConstantRef;
import java.lang.invoke.constant.EnumRef;
import java.lang.invoke.constant.MethodHandleRef;
import java.lang.invoke.constant.ConstantRefs;
import java.lang.invoke.constant.VarHandleRef;

import org.testng.annotations.Test;

import static java.lang.invoke.constant.ConstantRefs.CR_MethodHandle;
import static java.lang.invoke.constant.ConstantRefs.CR_Object;
import static java.lang.invoke.constant.ConstantRefs.CR_String;
import static java.lang.invoke.constant.ConstantRefs.CR_VarHandle;
import static java.lang.invoke.constant.ConstantRefs.CR_int;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

/**
 * @test
 * @run testng CondyRefTest
 * @summary unit tests for java.lang.invoke.constant.CondyRefTest
 */
@Test
public class CondyRefTest extends SymbolicRefTest {
    private final static ConstantRef<?>[] EMPTY_ARGS = new ConstantRef<?>[0];
    private final static ClassRef CR_ConstantBootstraps = ClassRef.of("java.lang.invoke.ConstantBootstraps");

    private static<T> void testDCR(DynamicConstantRef<T> r, T c) throws ReflectiveOperationException {
        assertEquals(r, DynamicConstantRef.of(r.bootstrapMethod(), r.constantName(), r.constantType(), r.bootstrapArgs()));
        assertEquals(r.resolveConstantRef(LOOKUP), c);
    }

    private void testVarHandleRef(DynamicConstantRef<VarHandle> r, VarHandle vh) throws ReflectiveOperationException  {
        testSymbolicRef(r);
        assertEquals(r.resolveConstantRef(LOOKUP), vh);
        assertEquals(vh.toConstantRef(LOOKUP).orElseThrow(), r);
    }

    private static<E extends Enum<E>> void testEnumRef(EnumRef<E> r, E e) throws ReflectiveOperationException {
        testSymbolicRef(r);

        assertEquals(r, EnumRef.of(r.constantType(), r.constantName()));
        assertEquals(r.resolveConstantRef(LOOKUP), e);
    }

    public void testNullConstant() throws ReflectiveOperationException {
        DynamicConstantRef<?> r = (DynamicConstantRef<?>) ConstantRefs.NULL;
        assertEquals(r, DynamicConstantRef.of(r.bootstrapMethod(), r.constantName(), r.constantType(), r.bootstrapArgs()));
        assertNull(r.resolveConstantRef(LOOKUP));
    }

    static String concatBSM(MethodHandles.Lookup lookup, String name, Class<?> type, String a, String b) {
        return a + b;
    }

    public void testDynamicConstant() throws ReflectiveOperationException {
        MethodHandleRef bsmRef = MethodHandleRef.ofDynamicConstant(ClassRef.of("CondyRefTest"), "concatBSM",
                                                                   CR_String, CR_String, CR_String);
        DynamicConstantRef<String> r = DynamicConstantRef.<String>of(bsmRef).withArgs("foo", "bar");
        testDCR(r, "foobar");
    }

    public void testNested() throws Throwable {
        MethodHandleRef invoker = MethodHandleRef.ofDynamicConstant(CR_ConstantBootstraps, "invoke", CR_Object, CR_MethodHandle, CR_Object.array());
        MethodHandleRef format = MethodHandleRef.of(MethodHandleRef.Kind.STATIC, CR_String, "format", CR_String, CR_String, CR_Object.array());

        String s = (String) invoker.resolveConstantRef(LOOKUP)
                                   .invoke(LOOKUP, "", String.class,
                                           format.resolveConstantRef(LOOKUP), "%s%s", "moo", "cow");
        assertEquals(s, "moocow");

        DynamicConstantRef<String> ref = DynamicConstantRef.<String>of(invoker).withArgs(format, "%s%s", "moo", "cow");
        testDCR(ref, "moocow");

        DynamicConstantRef<String> ref2 = DynamicConstantRef.<String>of(invoker).withArgs(format, "%s%s", ref, "cow");
        testDCR(ref2, "moocowcow");
    }

    enum MyEnum { A, B, C }

    public void testEnumRef() throws ReflectiveOperationException {
        ClassRef enumClass = ClassRef.of("CondyRefTest").inner("MyEnum");

        testEnumRef(EnumRef.of(enumClass, "A"), MyEnum.A);
        testEnumRef(EnumRef.of(enumClass, "B"), MyEnum.B);
        testEnumRef(EnumRef.of(enumClass, "C"), MyEnum.C);
    }

    static class MyClass {
        static int sf;
        int f;
    }

    public void testVarHandles() throws ReflectiveOperationException {
        ClassRef testClass = ClassRef.of("CondyRefTest").inner("MyClass");
        MyClass instance = new MyClass();

        // static varHandle
        VarHandleRef vhc = VarHandleRef.ofStaticField(testClass, "sf", CR_int);
        VarHandle varHandle = LOOKUP.findStaticVarHandle(MyClass.class, "sf", int.class);
        testVarHandleRef(vhc, varHandle);

        assertEquals(varHandle.varType(), int.class);
        varHandle.set(8);
        assertEquals(8, (int) varHandle.get());
        assertEquals(MyClass.sf, 8);

        // static varHandle
        vhc = VarHandleRef.ofField(testClass, "f", CR_int);
        varHandle = LOOKUP.findVarHandle(MyClass.class, "f", int.class);
        testVarHandleRef(vhc, varHandle);

        assertEquals(varHandle.varType(), int.class);
        varHandle.set(instance, 9);
        assertEquals(9, (int) varHandle.get(instance));
        assertEquals(instance.f, 9);

        vhc = VarHandleRef.ofArray(CR_int.array());
        varHandle = MethodHandles.arrayElementVarHandle(int[].class);
        testVarHandleRef(vhc, varHandle);

        int[] ints = new int[3];
        varHandle.set(ints, 0, 1);
        varHandle.set(ints, 1, 2);
        varHandle.set(ints, 2, 3);

        assertEquals(1, varHandle.get(ints, 0));
        assertEquals(2, varHandle.get(ints, 1));
        assertEquals(3, varHandle.get(ints, 2));
        assertEquals(1, ints[0]);
        assertEquals(2, ints[1]);
        assertEquals(3, ints[2]);
    }

    private<T> void assertLifted(ConstantRef<T> prototype,
                                 DynamicConstantRef<T> nonCanonical,
                                 ConstantRef<T> canonical) {
        Class<?> clazz = prototype.getClass();

        assertTrue(canonical != nonCanonical);
        assertTrue(clazz.isAssignableFrom(canonical.getClass()));
        assertFalse(clazz.isAssignableFrom(nonCanonical.getClass()));
        assertTrue(prototype.equals(canonical));
        assertTrue(canonical.equals(prototype));
        if (prototype instanceof DynamicConstantRef) {
            assertTrue(canonical.equals(nonCanonical));
            assertTrue(nonCanonical.equals(canonical));
            assertTrue(prototype.equals(nonCanonical));
            assertTrue(nonCanonical.equals(prototype));
        }
    }

    public void testLifting() {
        DynamicConstantRef<Object> unliftedNull = DynamicConstantRef.of(ConstantRefs.BSM_NULL_CONSTANT, "_", CR_Object, EMPTY_ARGS);
        assertEquals(ConstantRefs.NULL, unliftedNull);
        assertTrue(ConstantRefs.NULL != unliftedNull);
        assertTrue(ConstantRefs.NULL == DynamicConstantRef.ofCanonical(ConstantRefs.BSM_NULL_CONSTANT, "_", CR_Object, EMPTY_ARGS));
        assertTrue(ConstantRefs.NULL == DynamicConstantRef.ofCanonical(ConstantRefs.BSM_NULL_CONSTANT, "_", CR_String, EMPTY_ARGS));
        assertTrue(ConstantRefs.NULL == DynamicConstantRef.ofCanonical(ConstantRefs.BSM_NULL_CONSTANT, "wahoo", CR_Object, EMPTY_ARGS));

        assertLifted(CR_int,
                     DynamicConstantRef.of(ConstantRefs.BSM_PRIMITIVE_CLASS, "I", ConstantRefs.CR_Class, EMPTY_ARGS),
                     DynamicConstantRef.ofCanonical(ConstantRefs.BSM_PRIMITIVE_CLASS, "I", ConstantRefs.CR_Class, EMPTY_ARGS));

        ClassRef enumClass = ClassRef.of("CondyRefTest").inner("MyEnum");
        assertLifted(EnumRef.of(enumClass, "A"),
                     DynamicConstantRef.of(ConstantRefs.BSM_ENUM_CONSTANT, "A", enumClass, EMPTY_ARGS),
                     DynamicConstantRef.<MyEnum>ofCanonical(ConstantRefs.BSM_ENUM_CONSTANT, "A", enumClass, EMPTY_ARGS));

        ClassRef testClass = ClassRef.of("CondyRefTest").inner("MyClass");
        assertLifted(VarHandleRef.ofStaticField(testClass, "sf", CR_int),
                     DynamicConstantRef.of(ConstantRefs.BSM_VARHANDLE_STATIC_FIELD, "sf", CR_VarHandle, new ConstantRef<?>[] {testClass, "sf", CR_int }),
                     DynamicConstantRef.ofCanonical(ConstantRefs.BSM_VARHANDLE_STATIC_FIELD, "sf", CR_VarHandle, new ConstantRef<?>[] {testClass, "sf", CR_int }));
        assertLifted(VarHandleRef.ofField(testClass, "f", CR_int),
                     DynamicConstantRef.of(ConstantRefs.BSM_VARHANDLE_FIELD, "f", CR_VarHandle, new ConstantRef<?>[] {testClass, "f", CR_int }),
                     DynamicConstantRef.ofCanonical(ConstantRefs.BSM_VARHANDLE_FIELD, "f", CR_VarHandle, new ConstantRef<?>[] {testClass, "f", CR_int }));
        assertLifted(VarHandleRef.ofArray(CR_int.array()),
                     DynamicConstantRef.of(ConstantRefs.BSM_VARHANDLE_ARRAY, "_", CR_VarHandle, new ConstantRef<?>[] {CR_int.array() }),
                     DynamicConstantRef.ofCanonical(ConstantRefs.BSM_VARHANDLE_ARRAY, "_", CR_VarHandle, new ConstantRef<?>[] {CR_int.array() }));
    }

}
