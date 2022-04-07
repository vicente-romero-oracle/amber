/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

package java.lang;

import java.util.*;
import java.util.stream.Collectors;

import jdk.internal.javac.PreviewFeature;

/**
 * A {@link TemplatedString} object represents the information captured
 * from a templated string expression. This information is comprised of a
 * stencil and the values from the evaluation of embedded expressions.
 * The stencil is a {@link String} with placeholders where expressions
 * existed in the original templated string expression.
 * <p>
 * {@link TemplatedString} are primarily used in conjuction with {@link
 * TemplatePolicy} to produce useful results. For example, if a user needs
 * to produce a {@link String} by replacing placeholders with values then
 * they might use supplied {@link java.lang.TemplatePolicy#STR} policy.
 * {@snippet :
 * int x = 10;
 * int y = 20;
 * String result = STR."\{x} + \{y} = \{x + y}";
 * System.out.println(result);
 * }
 * Outputs: <code>10 + 20 = 30</code>
 * @implSpec An instance of {@link TemplatedString} is immutatble. The
 * number of placeholders in the stencil must equal the number of values
 * captured.
 *
 * @see java.lang.TemplatePolicy
 * @see java.util.FormatterPolicy
 */
@PreviewFeature(feature=PreviewFeature.Feature.TEMPLATED_STRINGS)
public interface TemplatedString {

    /**
     * Placeholder character marking insertion points in the stencil.
     * The value used is the unicode OBJECT REPLACEMENT CHARACTER
     * <code>(&#92;uFFFC)</code>.
     */
    public static final char PLACEHOLDER = '\uFFFC';

    /**
     * String equivalent of {@link PLACEHOLDER}.
     */
    public static final String PLACEHOLDER_STRING = Character.toString(PLACEHOLDER);

    /**
     * Returns the stencil string with placeholders. In the example:
     * {@snippet :
     * TemplatedString templatedString = "\{a} + \{b} = \{a + b}";
     * String stencil = templatedString.stencil(); // @highlight substring="stencil()"
     * }
     * <code>stencil</code> will be equivalent to <code>"&#92;uFFFC + &#92;uFFFC = &#92;uFFFC"</code>.
     *
     * @return the stencil string with placeholder
     */
    String stencil();

    /**
     * Returns an immutable list of expression values. In the example:
     * {@snippet :
     * TemplatedString templatedString = "\{a} + \{b} = \{a + b}";
     * List<Object> values = templatedString.values(); // @highlight substring="values()"
     * }
     * <code>values</code> will be equivalent to <code>List.of(a, b, a + b)</code>
     *
     * @return list of expression values
     */
    List<Object> values();

    /**
     * Returns an immutable list of string fragments created by splitting the
     * stencil at placeholders using {@link TemplatedString#split(String)}. In the example:
     * {@snippet :
     * TemplatedString templatedString = "The student \{student} is in \{teacher}'s class room.";
     * List<String> fragments = templatedString.fragments(); // @highlight substring="fragments()"
     * }
     * <code>fragments</code> will be equivalent to
     * <code>List.of("The student ", " is in ", "'s class room.")</code>
     * <p>
     * {@link fragments} is a convenience method for {@link TemplatePolicy
     * template policies} that construct results using {@link StringBuilder}.
     * Typically using the following pattern:
     * {@snippet :
     * StringBuilder sb = new StringBuilder();
     * Iterator<String> fragmentsIter = templatedString.fragments().iterator(); // @highlight substring="fragments()"
     *
     * for (Object value : templatedString.values()) {
     *     sb.append(fragmentsIter.next());
     *     sb.append(value);
     * }
     *
     * sb.append(fragmentsIter.next());
     * String result = sb.toString();
     * }
     * @implSpec The list returned is immutable.
     *
     * @implNote The {@link TemplatedString} implementation generated by the
     * compiler for a templated string expression guarantees efficiency by only
     * computing the fragments list once. Other implementations should make an
     * effort to do the same.
     *
     * @return list of string fragments
     */
    default List<String> fragments() {
        return TemplatedString.split(stencil());
    }

    /**
     * {@return the stencil with the values injected at placeholders}
     */
    default String concat() {
        return TemplatedString.concat(this);
    }

    /**
     * Returns the result of applying the specified policy to this
     * {@link TemplatedString}.
     *
     * @param policy the {@link TemplatePolicy} instance to apply
     *
     * @param <R>  Policy's apply result type.
     * @param <E>  Exception thrown type.
     *
     * @return constructed object of type R
     *
     * @throws E exception thrown by the template policy when validation fails
     */
    default <R, E extends Throwable> R apply(TemplatePolicy<R, E> policy) throws E {
        return policy.apply(this);
    }

    /**
     * Produces a diagnostic string representing the supplied
     * {@link TemplatedString}.
     *
     * @param templatedString  the {@link TemplatedString} to represent
     *
     * @return diagnostic string representing the supplied templated string
     *
     * @throws NullPointerException if templatedString is null
     */
    public static String toString(TemplatedString templatedString) {
        Objects.requireNonNull(templatedString, "templatedString is null");

        StringBuilder sb = new StringBuilder();
        String stencil = templatedString.stencil()
                .replace("\"", "\\\"")
                .replace(PLACEHOLDER_STRING, "\\{}");

        sb.append('"');
        sb.append(stencil);
        sb.append('"');
        sb.append('(');
        String prefix = "\"" + stencil + "\"(";
        String delimiter = ", ";
        String suffix = ")";

        return templatedString.values()
                .stream()
                .map(v -> String.valueOf(v))
                .collect(Collectors.joining(delimiter, prefix, suffix));
    }

    /**
     * Splits a stencil at placeholders, returning an immutable list of strings.
     *
     * @implSpec Unlike {@link String#split String.split} this method returns an
     * immutable list and retains empty edge cases (empty string and string
     * ending in placeholder) so that fragments prefix and suffix all placeholders
     * (expression values). That is, <code>fragments().size() == values().size()
     * + 1</code>.
     *
     * @param stencil  a {@link String} with placeholders
     *
     * @return list of string fragments
     *
     * @throws NullPointerException if string is null
     */
    public static List<String> split(String stencil) {
        Objects.requireNonNull(stencil, "stencil is null");

        List<String> fragments = new ArrayList<>(8);
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < stencil.length(); i++) {
            char ch = stencil.charAt(i);

            if (ch == PLACEHOLDER) {
                fragments.add(sb.toString());
                sb.setLength(0);
            } else {
                sb.append(ch);
            }
        }

        fragments.add(sb.toString());

        return Collections.unmodifiableList(fragments);
    }

    /**
     * {@return the stencil with the values inserted at placeholders}
     *
     * @param templatedString  the {@link TemplatedString} to concatenate
     *
     * @throws NullPointerException if templatedString is null
     */
    private static String concat(TemplatedString templatedString) {
        Objects.requireNonNull(templatedString, "templatedString is null");

        Iterator<String> fragmentsIter = templatedString.fragments().iterator();
        List<Object> values = templatedString.values();
        StringBuilder sb = new StringBuilder(templatedString.stencil().length() +
                                             16 * values.size());

        for (Object value : values) {
            sb.append(fragmentsIter.next());
            sb.append(value);
        }

        sb.append(fragmentsIter.next());

        return sb.toString();
    }

    /**
     * Returns a TemplatedString composed from a String.
     *
     * @implSpec The string can not contain expressions or OBJECT REPLACEMENT CHARACTER.
     *
     * @param string  a {@link String} to be composed into a {@link TemplatedString}.
     *
     * @return TemplatedString composed from string
     *
     * @throws IllegalArgumentException if string contains OBJECT REPLACEMENT CHARACTER.
     * @throws NullPointerException if string is null
     */
    public static TemplatedString of(String string) {
        Objects.requireNonNull(string, "string is null");

        if (string.indexOf(PLACEHOLDER) != -1) {
            throw new IllegalArgumentException("string contains an OBJECT REPLACEMENT CHARACTER");
        }

        return new TemplatedString() {
            private List<String> FRAGMENTS = List.of(string);

            @Override
            public String stencil() {
                return string;
            }

            @Override
            public List<Object> values() {
                return List.of();
            }

            @Override
            public List<String> fragments() {
                return FRAGMENTS;
            }

            @Override
            public String concat() {
                return string;
            }
        };
    }

    /**
     * Create a new {@link TemplatedString.Builder}.
     *
     * @return a new {@link TemplatedString.Builder}
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * This class can be used to construct a new TemplatedString from string
     * fragments, values and other {@link TemplatedString TemplatedStrings}.
     * <p>
     * To use, construct a new {@link Builder} using {@link TemplatedString#builder},
     * then chain invokes of {@link Builder#fragment(String)} or
     * {@link Builder#value(Object)} to build up the stencil and values.
     * {@link Builder#template(TemplatedString)} can be used to add the
     * stencil and values from another {@link TemplatedString}.
     * {@link Builder#build()} can be invoked at the end of the chain to produce a new
     * <p>
     * TemplatedString using the current state of the builder.
     * Example:
     *
     * {@snippet :
     *      int x = 10;
     *      int y = 20;
     *      TemplatedString ts = TemplatedString.builder()
     *          .fragment("The result of adding ")
     *          .value(x)
     *          .template(" and \{y} equals \{x + y}")
     *          .build();
     *      String result = STR.apply(ts);
     *  }
     *
     *  Result: "The result of adding 10 and 20 equals 30"
     * <p>
     * The {@link Builder} itself implements {@link TemplatedString}. When
     * applied to a policy will use the current state of stencil and values to
     * produce a result.
     */
    public static class Builder implements TemplatedString {
        /**
         * {@link StringBuilder} used to construct the final stencil.
         */
        private final StringBuilder stencilBuilder;

        /**
         * {@link ArrayList} used to gather values.
         */
        private final List<Object> values;

        /**
         * package private Constructor.
         */
        Builder() {
            this.stencilBuilder = new StringBuilder();
            this.values = new ArrayList<>();
        }

        @Override
        public String stencil() {
            return stencilBuilder.toString();
        }

        @Override
        public List<Object> values() {
            return Collections.unmodifiableList(values);
        }

        /**
         * Add the supplied {@link TemplatedString TemplatedString's} stencil and
         * values to the builder.
         *
         * @param templatedString existing TemplatedString
         *
         * @return this Builder
         *
         * @throws NullPointerException if templatedString is null
         */
        public Builder template(TemplatedString templatedString) {
            Objects.requireNonNull(templatedString, "templatedString is null");

            stencilBuilder.append(templatedString.stencil());
            values.addAll(templatedString.values());

            return this;
        }

        /**
         * Add a string fragment to the {@link Builder Builder's} stencil.
         *
         * @param fragment  string fragment to be added
         *
         * @return this Builder
         *
         * @throws IllegalArgumentException if fragment contains
         *         OBJECT REPLACEMENT CHARACTER
         * @throws NullPointerException if string is null
         */
        public Builder fragment(String fragment) {
            Objects.requireNonNull(fragment, "string is null");

            if (fragment.indexOf(PLACEHOLDER) != -1) {
                throw new IllegalArgumentException("string fragment an OBJECT REPLACEMENT CHARACTER");
            }

            stencilBuilder.append(fragment);

            return this;
        }

        /**
         * Add a value to the {@link Builder}. This method will also insert a placeholder
         * in the {@link Builder Builder's} stencil.
         *
         * @param value value to be added
         *
         * @return this Builder
         *
         * @throws NullPointerException if value is null
         */
        public Builder value(Object value) {
            Objects.requireNonNull(value, "value is null");

            stencilBuilder.append(PLACEHOLDER);
            values.add(value);

            return this;
        }

        /**
         * Returns a {@link TemplatedString} based on the current state of the
         * {@link Builder Builder's} stencil and values.
         *
         * @return a new TemplatedString
         */
        public TemplatedString build() {
            final String stencil = this.stencil();
            final List<Object> values = Collections.unmodifiableList(this.values());
            final List<String> fragments = TemplatedString.split(stencil);

            return new TemplatedString() {
                @Override
                public String stencil() {
                    return stencil;
                }

                @Override
                public List<Object> values() {
                    return values;
                }

                @Override
                public List<String> fragments() {
                    return fragments;
                }
            };
        }
    }
}
