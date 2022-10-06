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

package java.lang.template;

import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

import jdk.internal.javac.PreviewFeature;

/**
 * This interface describes the methods provided by a generalized string template processor. The
 * primary method {@link TemplateProcessor#process} is used to validate and compose a result using
 * a {@link StringTemplate StringTemplate's} fragments and values lists. For example:
 *
 * {@snippet :
 * class MyProcessor implements TemplateProcessor<String, IllegalArgumentException> {
 *     @Override
 *     public String process(StringTemplate st) throws IllegalArgumentException {
 *          StringBuilder sb = new StringBuilder();
 *          Iterator<String> fragmentsIter = st.fragments().iterator();
 *
 *          for (Object value : st.values()) {
 *              sb.append(fragmentsIter.next());
 *
 *              if (value instanceof Boolean) {
 *                  throw new IllegalArgumentException("I don't like Booleans");
 *              }
 *
 *              sb.append(value);
 *          }
 *
 *          sb.append(fragmentsIter.next());
 *
 *          return sb.toString();
 *     }
 * }
 *
 * MyProcessor myProcessor = new MyProcessor();
 * try {
 *     int x = 10;
 *     int y = 20;
 *     String result = myProcessor."\{x} + \{y} = \{x + y}";
 *     ...
 * } catch (IllegalArgumentException ex) {
 *     ...
 * }
 * }
 * Implementations of this interface may provide, but are not limited to, validating
 * inputs, composing inputs into a result, and transforming an intermediate string
 * result to a non-string value before delivering the final result.
 * <p>
 * The user has the option of validating inputs used in composition. For example an SQL
 * processor could prevent injection vulnerabilities by sanitizing inputs or throwing an
 * exception of type {@code E} if an SQL statement is a potential vulnerability.
 * <p>
 * Composing allows user control over how the result is assembled. Most often, a
 * user will construct a new string from the template string, with placeholders
 * replaced by stringified objects from the values list.
 * <p>
 * Transforming allows the processor to return something other than a string. For
 * instance, a JSON processor could return a JSON object, by parsing the string created
 * by composition, instead of the composed string.
 * <p>
 * {@link TemplateProcessor} is a {@link FunctionalInterface}. This permits declaration of a
 * processor using lambda expressions;
 * {@snippet :
 * TemplateProcessor<String, RuntimeException> templateProcessor = st -> {
 *     List<String> fragments = st.fragments();
 *     List<Object> values = st.values();
 *     // check or manipulate the fragments and/or values
 *     ...
 *     return StringTemplate.interpolate(fragments, values);;
 * };
 * }
 * The {@link FunctionalInterface} {@link SimpleProcessor} is supplied to avoid
 * declaring checked exceptions;
 * {@snippet :
 * SimpleProcessor<String> simpleProcessor = st -> {
 *     List<String> fragments = st.fragments();
 *     List<Object> values = st.values();
 *     // check or manipulate the fragments and/or values
 *     ...
 *     return StringTemplate.interpolate(fragments, values);
 * };
 * }
 * The {@link FunctionalInterface} {@link StringProcessor} is supplied if
 * the processor returns {@link String};
 * {@snippet :
 * StringProcessor stringProcessor = st -> {
 *     List<String> fragments = st.fragments();
 *     List<Object> values = st.values();
 *     // check or manipulate the fragments and/or values
 *     ...
 *     return StringTemplate.interpolate(fragments, values);
 * };
 * }
 * The {@link StringTemplate#interpolate()} method is available for those processors
 * that just need to work with the interpolatation;
 * {@snippet :
 * StringProcessor simpleProcessor = StringTemplate::interpolate;
 * }
 * or simply transform the interpolatation into something other than
 * {@link String};
 * {@snippet :
 * SimpleProcessor<JSONObject> jsonProcessor = st -> new JSONObject(st.interpolate());
 * }
 * @implNote The Java compiler automatically imports
 * {@link StringTemplate#STR} and {@link StringTemplate#FMT}
 *
 * @param <R>  Processor's process result type
 * @param <E>  Exception thrown type
 *
 * @see java.lang.template.StringTemplate
 * @see java.lang.template.SimpleProcessor
 * @see java.lang.template.StringProcessor
 * @see java.util.FormatProcessor
 *
 * @since 20
 */
@PreviewFeature(feature=PreviewFeature.Feature.STRING_TEMPLATES)
@FunctionalInterface
public interface TemplateProcessor<R, E extends Throwable> {

    /**
     * Constructs a result based on the template string and values in the
     * supplied {@link StringTemplate stringTemplate} object.
     *
     * @param stringTemplate  a {@link StringTemplate} instance
     *
     * @return constructed object of type R
     *
     * @throws E exception thrown by the template processor when validation fails
     */
    R process(StringTemplate stringTemplate) throws E;

}
