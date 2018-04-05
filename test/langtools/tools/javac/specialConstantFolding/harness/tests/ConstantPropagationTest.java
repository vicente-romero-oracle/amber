/* /nodynamiccopyright/ */

import java.lang.invoke.*;
import java.lang.invoke.constant.ClassRef;
import java.lang.invoke.constant.MethodTypeRef;

import static java.lang.invoke.Intrinsics.*;

@SkipExecution
public class ConstantPropagationTest extends ConstantFoldingTest {
    @InstructionInfo(bytecodePosition=0, values={"CONSTANT_MethodType_info", "()LConstantPropagationTest;"})
    void test() throws Throwable {
        ClassRef c = ClassRef.ofDescriptor("LConstantPropagationTest;");
        ClassRef d = c;  // constant!
        MethodType mt1 = ldc(MethodTypeRef.of(d)); // constant!
    }
}
