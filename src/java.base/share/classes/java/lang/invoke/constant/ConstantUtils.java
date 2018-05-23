/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
package java.lang.invoke.constant;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;

import sun.invoke.util.Wrapper;

/**
 * ConstantUtils
 *
 * @author Brian Goetz
 */
class ConstantUtils {
    static final ConstantDesc<?>[] EMPTY_CONSTANTDESC = new ConstantDesc<?>[0];
    static final Constable<?>[] EMPTY_CONSTABLE = new Constable<?>[0];

    private static final Set<String> pointyNames = Set.of("<init>", "<clinit>");

    static String validateBinaryClassName(String name) {
        for (int i=0; i<name.length(); i++) {
            char ch = name.charAt(i);
            if (ch == ';' || ch == '[' || ch == '/')
                throw new IllegalArgumentException("Invalid class name: " + name);
        }
        return name;
    }

    static String validateMemberName(String name) {
        if (name.length() == 0)
            throw new IllegalArgumentException("zero-length member name");
        for (int i=0; i<name.length(); i++) {
            char ch = name.charAt(i);
            if (ch == '.' || ch == ';' || ch == '[' || ch == '/')
                throw new IllegalArgumentException("Invalid member name: " + name);
            if (ch == '<' || ch == '>') {
                if (!pointyNames.contains(name))
                    throw new IllegalArgumentException("Invalid member name: " + name);
            }
        }
        return name;
    }

    static void validateClassOrInterface(ClassDesc clazz) {
        if (!clazz.isClassOrInterface())
            throw new IllegalArgumentException("not a class or interface type: " + clazz);
    }

    static int arrayDepth(String descriptorString) {
        int depth = 0;
        while (descriptorString.charAt(depth) == '[')
            depth++;
        return depth;
    }

    static String binaryToInternal(String name) {
        return name.replace('.', '/');
    }

    static String internalToBinary(String name) {
        return name.replace('/', '.');
    }

    static String dropLastChar(String s) {
        return s.substring(0, s.length() - 1);
    }

    static String dropFirstAndLastChar(String s) {
        return s.substring(1, s.length() - 1);
    }

    /**
     * Produce an {@code Optional<DynamicConstantDesc<T>>} describing the invocation
     * of the specified bootstrap with the specified arguments.  The arguments will
     * be converted to nominal descriptors using the provided lookup.  Helper
     * method for implementing {@link Constable#describeConstable()}.
     *
     * @param <T> the type of the resulting constant
     * @param bootstrap nominal descriptor for the bootstrap method
     * @param type nominal descriptor for the type of the resulting constant
     * @param args nominal descriptors for the bootstrap arguments
     * @return the nominal descriptor for the dynamic constant
     */
    static<T> Optional<DynamicConstantDesc<T>> symbolizeHelper(MethodHandleDesc bootstrap,
                                                               ClassDesc type,
                                                               Constable<?>... args) {
        try {
            ConstantDesc<?>[] quotedArgs = new ConstantDesc<?>[args.length + 1];
            quotedArgs[0] = bootstrap;
            for (int i=0; i<args.length; i++)
                quotedArgs[i+1] = args[i].describeConstable().orElseThrow();
            return Optional.of(DynamicConstantDesc.of(ConstantDescs.BSM_INVOKE, ConstantDescs.DEFAULT_NAME,
                                                      type, quotedArgs));
        }
        catch (NoSuchElementException e) {
            return Optional.empty();
        }
    }

    /**
     * Parse a method descriptor string, and return a list of field descriptor
     * strings, return type first, then parameter types
     *
     * @param descriptor the descriptor string
     * @return the list of types
     * @throws IllegalArgumentException if the descriptor string is not valid
     */
    static List<String> parseMethodDescriptor(String descriptor) {
        int cur = 0, end = descriptor.length();
        ArrayList<String> ptypes = new ArrayList<>();

        if (cur >= end || descriptor.charAt(cur) != '(')
            throw new IllegalArgumentException("Bad method descriptor: " + descriptor);

        ++cur;  // skip '('
        while (cur < end && descriptor.charAt(cur) != ')') {
            int len = matchSig(descriptor, cur, end);
            if (len == 0 || descriptor.charAt(cur) == 'V')
                throw new IllegalArgumentException("Bad method descriptor: " + descriptor);
            ptypes.add(descriptor.substring(cur, cur + len));
            cur += len;
        }
        if (cur >= end)
            throw new IllegalArgumentException("Bad method descriptor: " + descriptor);
        ++cur;  // skip ')'

        int rLen = matchSig(descriptor, cur, end);
        if (rLen == 0 || cur + rLen != end)
            throw new IllegalArgumentException("Bad method descriptor: " + descriptor);
        ptypes.add(0, descriptor.substring(cur, cur + rLen));
        return ptypes;
    }

    /**
     * Validate that the characters at [start, end) within the provided string
     * describe a valid field type descriptor.
     *
     * @param str the descriptor string
     * @param start the starting index into the string
     * @param end the ending index within the string
     * @return the length of the descriptor, or 0 if it is not a descriptor
     * @throws IllegalArgumentException if the descriptor string is not valid
     */
    static int matchSig(String str, int start, int end) {
        if (start >= end || start >= str.length() || end > str.length())
            return 0;
        char c = str.charAt(start);
        if (c == 'L') {
            int endc = str.indexOf(';', start);
            int badc = str.indexOf('.', start);
            if (badc >= 0 && badc < endc)
                return 0;
            badc = str.indexOf('[', start);
            if (badc >= 0 && badc < endc)
                return 0;
            return (endc < 0) ? 0 : endc - start + 1;
        } else if (c == '[') {
            int t = matchSig(str, start+1, end);
            return (t > 0) ? t + 1 : 0;
        } else {
            return ("IJCSBFDZV".indexOf(c) >= 0) ? 1 : 0;
        }
    }
}
