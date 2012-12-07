import java.util.Map;

import cs132.vapor.ast.VAssign;
import cs132.vapor.ast.VBranch;
import cs132.vapor.ast.VBuiltIn;
import cs132.vapor.ast.VCall;
import cs132.vapor.ast.VGoto;
import cs132.vapor.ast.VInstr.VisitorPR;
import cs132.vapor.ast.VMemRead;
import cs132.vapor.ast.VMemRef.Global;
import cs132.vapor.ast.VMemWrite;
import cs132.vapor.ast.VOperand;
import cs132.vapor.ast.VReturn;


public class SecondPassVisitor extends VisitorPR<Object, Object, Exception> {

	@Override
	public Object visit(Object p, VAssign a) throws Exception {
		InputSecondPass input = (InputSecondPass) p;
		String dest = getVariable(input.variableToRegister, a.dest.toString());
		String source = getVariable(input.variableToRegister, a.source.toString());
		String code = "  " + dest + " = " + source + "\n";
		return code;
	}

	@Override
	public Object visit(Object p, VCall c) throws Exception {
		InputSecondPass input = (InputSecondPass) p;
		
		String setupArguments = "";
		for (int argIndex = 0; argIndex < c.args.length && argIndex < 4; argIndex++)
		{
			String argReg = getVariable(input.variableToRegister, c.args[argIndex].toString());
			String assignArg = "  $a" + Integer.toString(argIndex) + " = " + argReg + "\n";
			setupArguments = setupArguments + assignArg;
		}
		for (int argIndex = 4; argIndex < c.args.length; argIndex++)
		{
			String argReg = getVariable(input.variableToRegister, c.args[argIndex].toString());
			String assignArg = "  out[" + Integer.toString(argIndex-4) + "] = " + argReg + "\n";
			setupArguments = setupArguments + assignArg;
		}
		
		String funcAddr = getVariable(input.variableToRegister, c.addr.toString());
		String call = "  call " + funcAddr + "\n";
		
		String assignReturnValue = "";
		if (c.dest != null)
		{
			String destRegister = getVariable(input.variableToRegister, c.dest.ident);
			assignReturnValue = "  " + destRegister + " = $v0\n";
		}
		String code = setupArguments + call + assignReturnValue;
		return code;
	}

	@Override
	public Object visit(Object p, VBuiltIn c) throws Exception {
		InputSecondPass input = (InputSecondPass) p;
		String code = "";
		String operand0 = "";
		String dest = "";
		switch (c.op.name)
		{
		case "Add":
		case "Sub":
		case "MulS":
		case "Eq":
		case "Lt":
		case "LtS":
			dest = getVariable(input.variableToRegister, c.dest.toString());
			operand0 = getVariable(input.variableToRegister, c.args[0].toString());
			String operand1 = getVariable(input.variableToRegister, c.args[1].toString());
			code = "  " + dest + " = " + c.op.name + "(" + operand0 + " " + operand1 + ")\n";
			break;
		case "PrintIntS":
			operand0 = getVariable(input.variableToRegister, c.args[0].toString());
			code = "  PrintIntS(" + operand0 + ")\n";
			break;
		case "HeapAllocZ":
			dest = getVariable(input.variableToRegister, c.dest.toString());
			operand0 = getVariable(input.variableToRegister, c.args[0].toString());
			code = "  " + dest + " = HeapAllocZ(" + operand0 + ")\n";
			break;
		case "Error":
			code = "  Error(" + c.args[0].toString() + ")\n";
			break;
		default:
			throw(new Exception("bad op name at line " + c.sourcePos.line + " col " + c.sourcePos.column));
		}
		
		return code;
	}

	@Override
	public Object visit(Object p, VMemWrite w) throws Exception {
		InputSecondPass input = (InputSecondPass) p;
		String code = "vmemwrite error\n";
		String sourceReg = getVariable(input.variableToRegister, w.source.toString());
		if (Global.class.isInstance(w.dest))
		{
			Global dest = (Global) w.dest;
			String destReg = getVariable(input.variableToRegister, dest.base.toString());
			code = "  [" + destReg + (dest.byteOffset >= 0 ? "+" : "") + Integer.toString(dest.byteOffset) + "] = " + sourceReg + "\n";
		}
		return code;
	}

	@Override
	public Object visit(Object p, VMemRead r) throws Exception {
		InputSecondPass input = (InputSecondPass) p;
		String code = "vmemRead error\n";
		String destReg = getVariable(input.variableToRegister, r.dest.toString());
		if (Global.class.isInstance(r.source))
		{
			Global source = (Global) r.source;
			String sourceReg = getVariable(input.variableToRegister, source.base.toString());
			code = "  " + destReg + " = [" + sourceReg + (source.byteOffset >= 0 ? "+" : "") + Integer.toString(source.byteOffset) + "]\n";  
		}
		return code;
	}

	@Override
	public Object visit(Object p, VBranch b) throws Exception {
		InputSecondPass input = (InputSecondPass) p;
		String condRegister = getVariable(input.variableToRegister, b.value.toString());
		String code = "  if" + (b.positive ? " " : "0 ") + condRegister + " goto :" + b.target.ident + "\n";
		return code;
	}

	@Override
	public Object visit(Object p, VGoto g) throws Exception {
		String code = "  goto " + g.target.toString() + "\n";
		return code;
	}

	@Override
	public Object visit(Object p, VReturn r) throws Exception {
		InputSecondPass input = (InputSecondPass) p;
		String setReturnValue = "";
		if (r.value != null)
		{
			String valueReg = getVariable(input.variableToRegister, r.value.toString());
			setReturnValue = "  $v0 = " + valueReg + "\n";
		}
		String code = setReturnValue + "  ret\n";
		return code;
	}

	private String getVariable(Map<String, String> variableToRegister, String name) 
	{
		String ret = name;
		if (variableToRegister.containsKey(name))
			ret = variableToRegister.get(name);
		return ret;
	}

}
