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
		
		String code = "";
		
		String source = getVariable(input.variableToRegister, a.source.toString());
		String sourceReg = source;
		String setupSource = "";
		if (source.startsWith("l"))
		{
			setupSource = "  $v1 = " + source + "\n";
			sourceReg = "$v1";
		}
		
		String dest = getVariable(input.variableToRegister, a.dest.toString());
		if (dest != null)
		{
			String destReg = dest;
			String setupDest = "";
			if (dest.startsWith("l"))
			{
				setupDest = "  " + dest + " = $v1\n";
				destReg = "$v1";
			}
			
			code = setupSource + "  " + destReg + " = " + sourceReg + "\n" + setupDest;
		}
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
			String arg = getVariable(input.variableToRegister, c.args[argIndex].toString());
			String argReg = arg;
			String setupArg = "";
			if (arg.startsWith("l"))
			{
				setupArg = "  $v1 = " + arg + "\n";
				argReg = "$v1";
			}
			
			String assignArg = setupArg + "  out[" + Integer.toString(argIndex-4) + "] = " + argReg + "\n";
			setupArguments = setupArguments + assignArg;
		}
		
		String setupCall = "";
		String funcAddr = getVariable(input.variableToRegister, c.addr.toString());
		String funcReg = funcAddr;
		if (funcAddr.startsWith("l"))
		{
			setupCall = "  $v1 = " + funcAddr + "\n";
			funcReg = "$v1";
		}
		String call = setupCall + "  call " + funcReg + "\n";
		
		String assignReturnValue = "";
		if (c.dest != null)
		{
			String setupDest = "";
			String dest = getVariable(input.variableToRegister, c.dest.ident);
			if (dest != null)
			{
				String destReg = dest;
				if (dest.startsWith("l"))
				{
					setupDest = "  " + dest + " = $v1\n";
					destReg = "$v1";
				}
				assignReturnValue = "  " + destReg + " = $v0\n" + setupDest;
			}
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
		String destReg = "";
		String setupDest = "";
		String operand0Reg = "";
		String setupOperand0 = "";
		switch (c.op.name)
		{
		case "Add":
		case "Sub":
		case "MulS":
		case "Eq":
		case "Lt":
		case "LtS":
			operand0 = getVariable(input.variableToRegister, c.args[0].toString());
			operand0Reg = operand0;
			setupOperand0 = "";
			if (operand0.startsWith("l"))
			{
				setupOperand0 = "  $v0 = " + operand0 + "\n";
				operand0Reg = "$v0";
			}
			
			String operand1 = getVariable(input.variableToRegister, c.args[1].toString());
			String operand1Reg = operand1;
			String setupOperand1 = "";
			if (operand1.startsWith("l"))
			{
				setupOperand1 = "  $v1 = " + operand1 + "\n";
				operand1Reg = "$v1";
			}
			
			dest = getVariable(input.variableToRegister, c.dest.toString());
			destReg = dest;
			setupDest = "";
			if (dest.startsWith("l"))
			{
				setupDest = "  " + dest + " = $v1\n";
				destReg = "$v1";
			}
			
			code = setupOperand0 + setupOperand1 + "  " + destReg + " = " + c.op.name + "(" + operand0Reg + " " + operand1Reg + ")\n" + setupDest;
			break;
		case "PrintIntS":
			operand0 = getVariable(input.variableToRegister, c.args[0].toString());
			operand0Reg = operand0;
			setupOperand0 = "";
			if (operand0.startsWith("l"))
			{
				setupOperand0 = "  $v0 = " + operand0 + "\n";
				operand0Reg = "$v0";
			}
			
			code = setupOperand0 + "  PrintIntS(" + operand0Reg + ")\n";
			break;
		case "HeapAllocZ":
			operand0 = getVariable(input.variableToRegister, c.args[0].toString());
			operand0Reg = operand0;
			setupOperand0 = "";
			if (operand0.startsWith("l"))
			{
				setupOperand0 = "  $v0 = " + operand0 + "\n";
				operand0Reg = "$v0";
			}
			
			dest = getVariable(input.variableToRegister, c.dest.toString());
			destReg = dest;
			setupDest = "";
			if (dest.startsWith("l"))
			{
				setupDest = "  " + dest + " = $v1\n";
				destReg = "$v1";
			}
			
			code = setupOperand0 + "  " + destReg + " = HeapAllocZ(" + operand0Reg + ")\n" + setupDest;
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
		
		String source = getVariable(input.variableToRegister, w.source.toString());
		String sourceReg = source;
		String setupSource = "";
		if (source.startsWith("l"))
		{
			setupSource = "  $v1 = " + source + "\n";
			sourceReg = "$v1";
		}
		
		if (Global.class.isInstance(w.dest))
		{
			Global addr = (Global) w.dest;
			String dest = getVariable(input.variableToRegister, addr.base.toString());
			String destReg = dest;
			String setupDest = "";
			if (dest.startsWith("l"))
			{
				setupDest = "  $v0 = " + dest + "\n";
				destReg = "$v0";
			}
			
			code = setupSource + setupDest + "  [" + destReg + (addr.byteOffset >= 0 ? "+" : "") + Integer.toString(addr.byteOffset) + "] = " + sourceReg + "\n";
		}
		return code;
	}

	@Override
	public Object visit(Object p, VMemRead r) throws Exception {
		InputSecondPass input = (InputSecondPass) p;
		String code = "vmemRead error\n";
		
		String dest = getVariable(input.variableToRegister, r.dest.toString());
		String destReg = dest;
		String setupDest = "";
		if (dest.startsWith("l"))
		{
			setupDest = "  $v0 = " + dest + "\n";
			destReg = "$v0";
		}
		
		if (Global.class.isInstance(r.source))
		{
			Global addr = (Global) r.source;
			String source = getVariable(input.variableToRegister, addr.base.toString());
			String sourceReg = source;
			String setupSource = "";
			if (source.startsWith("l"))
			{
				setupSource = "  $v1 = " + source + "\n";
				sourceReg = "$v1";
			}
			
			code = setupDest + setupSource + "  " + destReg + " = [" + sourceReg + (addr.byteOffset >= 0 ? "+" : "") + Integer.toString(addr.byteOffset) + "]\n";  
		}
		return code;
	}

	@Override
	public Object visit(Object p, VBranch b) throws Exception {
		InputSecondPass input = (InputSecondPass) p;
		String cond = getVariable(input.variableToRegister, b.value.toString());
		String condReg = cond;
		String setupCond = "";
		if (cond.startsWith("l"))
		{
			setupCond = "  $v1 = " + cond + "\n";
			condReg = "$v1";
		}
		
		String code = setupCond + "  if" + (b.positive ? " " : "0 ") + condReg + " goto :" + b.target.ident + "\n";
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
		if (isNumeric(name) || name.startsWith(":"))
			return name;
		if (variableToRegister.containsKey(name))
			return variableToRegister.get(name);
		return null;
	}
	
	private boolean isNumeric(String str)  
	{
		try 
		{ 
			int i = Integer.parseInt(str);
		} catch(NumberFormatException nfe)  
		{ 
			return false;
		}
		
		return true;  
	}

}
