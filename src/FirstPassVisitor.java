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
import cs132.vapor.ast.VVarRef.Local;


public class FirstPassVisitor extends VisitorPR<Object, Object, Exception> {

	@Override
	public Object visit(Object p, VAssign a) throws Exception {
		Input input = (Input) p;
		Local dest = (Local) a.dest;
		updateRange(input.variableLives, dest.ident, dest.sourcePos.line);
			
		return null;
	}

	@Override
	public Object visit(Object p, VCall c) throws Exception {
		Input input = (Input) p;
		
		input.isLeaf = false;
		
		if ((c.args.length - 4) > input.largestOut)
			input.largestOut = c.args.length - 4;
		
		for (VOperand argument : c.args)
		{
			updateRange(input.variableLives, argument.toString(), argument.sourcePos.line);
		}
		return null;
	}

	@Override
	public Object visit(Object p, VBuiltIn c) throws Exception {
		Input input = (Input) p;
		switch (c.op.name)
		{
		case "Add":
		case "Sub":
		case "MulS":
		case "Eq":
		case "Lt":
		case "LtS":
			updateRange(input.variableLives, c.args[0].toString(), c.args[0].sourcePos.line);
			updateRange(input.variableLives, c.args[1].toString(), c.args[1].sourcePos.line);
			updateRange(input.variableLives, c.dest.toString(), c.dest.sourcePos.line);
			break;
		case "PrintIntS":
			updateRange(input.variableLives, c.args[0].toString(), c.args[0].sourcePos.line);
			break;
		case "HeapAllocZ":
			updateRange(input.variableLives, c.args[0].toString(), c.args[0].sourcePos.line);
			updateRange(input.variableLives, c.dest.toString(), c.dest.sourcePos.line);
			break;
		case "Error":
			break;
		default:
			throw(new Exception("bad op name at line " + c.sourcePos.line + " col " + c.sourcePos.column));
		}
		
		return null;
	}

	@Override
	public Object visit(Object p, VMemWrite w) throws Exception {
		Input input = (Input) p;
		Global dest = (Global) w.dest;
		updateRange(input.variableLives, dest.base.toString(), dest.sourcePos.line);
		updateRange(input.variableLives, w.source.toString(), w.sourcePos.line);
		return null;
	}

	@Override
	public Object visit(Object p, VMemRead r) throws Exception {
		Input input = (Input) p;
		Global source = (Global) r.source;
		updateRange(input.variableLives, source.base.toString(), source.sourcePos.line);
		updateRange(input.variableLives, r.dest.toString(), r.dest.sourcePos.line);
		return null;
	}

	@Override
	public Object visit(Object p, VBranch b) throws Exception {
		Input input = (Input) p;
		updateRange(input.variableLives, b.value.toString(), b.value.sourcePos.line);
		return null;
	}

	@Override
	public Object visit(Object p, VGoto g) throws Exception {
		return null;
	}

	@Override
	public Object visit(Object p, VReturn r) throws Exception {
		Input input = (Input) p;
		if (r.value != null)
			updateRange(input.variableLives, r.value.toString(), r.value.sourcePos.line);
		return null;
	}

	private void updateRange(Map<String, Range> variableLives, String name,	int line) 
	{
		if (isNumeric(name) || name.startsWith(":"))
			return;
		Range range = variableLives.get(name);
		if (range != null)
		{
			Range newRange = new Range(name, range.begin, line);
			variableLives.put(name, newRange);
		}
		else
		{
			Range newRange = new Range(name, line, line);
			variableLives.put(name, newRange);
		}
		
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
