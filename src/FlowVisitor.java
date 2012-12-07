import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import cs132.vapor.ast.VAssign;
import cs132.vapor.ast.VBranch;
import cs132.vapor.ast.VBuiltIn;
import cs132.vapor.ast.VCall;
import cs132.vapor.ast.VCodeLabel;
import cs132.vapor.ast.VGoto;
import cs132.vapor.ast.VInstr.VisitorPR;
import cs132.vapor.ast.VMemRead;
import cs132.vapor.ast.VMemRef.Global;
import cs132.vapor.ast.VMemWrite;
import cs132.vapor.ast.VOperand;
import cs132.vapor.ast.VReturn;


public class FlowVisitor extends VisitorPR<Object, Object, Exception> {

	@Override
	public Object visit(Object p, VAssign a) throws Exception {
		FlowInput input = (FlowInput) p;
		
		String dest = a.dest.toString();
		updateMap(input.variableAssignments, dest, a.sourcePos.line);
		
		String source = a.source.toString();
		updateMap(input.variableUses, source, a.sourcePos.line);
		
		int nextLine = getNextInstructionLine(input.labels, a.sourcePos.line);
		Set<Integer> nextLines = new HashSet<Integer>();
		nextLines.add(nextLine);
		return new FlowNode(a.sourcePos.line, nextLines, null);
	}

	@Override
	public Object visit(Object p, VCall c) throws Exception {
		FlowInput input = (FlowInput) p;
		
		input.isLeaf = false;
		
		if ((c.args.length - 4) > input.largestOut)
			input.largestOut = c.args.length - 4;
		
		int line = c.sourcePos.line;
		
		if (c.dest != null)
		{
			String dest = c.dest.toString();
			updateMap(input.variableAssignments, dest, line);
		}
		
		String callVar = c.addr.toString();
		updateMap(input.variableUses, callVar, line);
		
		for (VOperand argument : c.args)
		{
			String arg = argument.toString();
			updateMap(input.variableUses, arg, line);
		}
		
		int nextLine = getNextInstructionLine(input.labels, line);
		Set<Integer> nextLines = new HashSet<Integer>();
		nextLines.add(nextLine);
		return new FlowNode(line, nextLines, null);
	}

	@Override
	public Object visit(Object p, VBuiltIn c) throws Exception {
		FlowInput input = (FlowInput) p;
		
		int line = c.sourcePos.line;
		
		switch (c.op.name)
		{
		case "Add":
		case "Sub":
		case "MulS":
		case "Eq":
		case "Lt":
		case "LtS":
			updateMap(input.variableUses, c.args[0].toString(), line);
			updateMap(input.variableUses, c.args[1].toString(), line);
			updateMap(input.variableAssignments, c.dest.toString(), line);
			break;
		case "PrintIntS":
			updateMap(input.variableUses, c.args[0].toString(), line);
			break;
		case "HeapAllocZ":
			updateMap(input.variableUses, c.args[0].toString(), line);
			updateMap(input.variableAssignments, c.dest.toString(), line);
			break;
		case "Error":
			break;
		default:
			throw(new Exception("bad op name at line " + c.sourcePos.line + " col " + c.sourcePos.column));
		}
		
		int nextLine = getNextInstructionLine(input.labels, c.sourcePos.line);
		Set<Integer> nextLines = new HashSet<Integer>();
		nextLines.add(nextLine);
		return new FlowNode(c.sourcePos.line, nextLines, null);
	}

	@Override
	public Object visit(Object p, VMemWrite w) throws Exception {
		FlowInput input = (FlowInput) p;
		
		int line = w.sourcePos.line;
		
		Global dest = (Global) w.dest;
		updateMap(input.variableUses, dest.base.toString(), line);
		
		updateMap(input.variableUses, w.source.toString(), line);
		
		int nextLine = getNextInstructionLine(input.labels, w.sourcePos.line);
		Set<Integer> nextLines = new HashSet<Integer>();
		nextLines.add(nextLine);
		return new FlowNode(w.sourcePos.line, nextLines, null);
	}

	@Override
	public Object visit(Object p, VMemRead r) throws Exception {
		FlowInput input = (FlowInput) p;
		
		int line = r.sourcePos.line;
		
		Global source = (Global) r.source;
		updateMap(input.variableUses, source.base.toString(), line);
		
		updateMap(input.variableUses, r.dest.toString(), line);
		
		int nextLine = getNextInstructionLine(input.labels, r.sourcePos.line);
		Set<Integer> nextLines = new HashSet<Integer>();
		nextLines.add(nextLine);
		return new FlowNode(r.sourcePos.line, nextLines, null);
	}

	@Override
	public Object visit(Object p, VBranch b) throws Exception {
		FlowInput input = (FlowInput) p;
		
		updateMap(input.variableUses, b.value.toString(), b.sourcePos.line);
		
		int nextLine = getNextInstructionLine(input.labels, b.sourcePos.line);
		Set<Integer> nextLines = new HashSet<Integer>();
		nextLines.add(nextLine);
		for (VCodeLabel label : input.labels)
		{
			if (label.ident.equals(b.target.ident))
			{
				nextLines.add(getNextInstructionLine(input.labels, label.sourcePos.line));
			}
		}
		return new FlowNode(b.sourcePos.line, nextLines, null);
	}

	@Override
	public Object visit(Object p, VGoto g) throws Exception {
		FlowInput input = (FlowInput) p;
		int nextLine = getNextInstructionLine(input.labels, g.sourcePos.line);
		Set<Integer> nextLines = new HashSet<Integer>();
		nextLines.add(nextLine);
		for (VCodeLabel label : input.labels)
		{
			if (label.ident.equals(g.target.toString()))
			{
				nextLines.add(getNextInstructionLine(input.labels, label.sourcePos.line));
			}
		}
		return new FlowNode(g.sourcePos.line, nextLines, null);
	}

	@Override
	public Object visit(Object p, VReturn r) throws Exception {
		FlowInput input = (FlowInput) p;
		
		if (r.value != null)
		{
			updateMap(input.variableUses, r.value.toString(), r.sourcePos.line);
		}
		
		Set<Integer> nextLines = new HashSet<Integer>();
		return new FlowNode(r.sourcePos.line, nextLines, null);
	}

	private int getNextInstructionLine(VCodeLabel[] labels, int line) {
		int nextLine = line+1;
		int labelIndex = 0;
		while (labelIndex < labels.length && labels[labelIndex].sourcePos.line < nextLine)
		{
			labelIndex++;
		}
		while (labelIndex < labels.length && labels[labelIndex].sourcePos.line == nextLine)
		{
			nextLine++;
			labelIndex++;
		}
		return nextLine;
	}
	
	private void updateMap(Map<String, SortedSet<Integer>> map, String variable, int line)
	{
		if (isNumeric(variable) || variable.startsWith(":"))
			return;
		if (map.containsKey(variable))
		{
			SortedSet<Integer> assignments = map.get(variable);
			SortedSet<Integer> newAssignments = new TreeSet<Integer>(assignments);
			newAssignments.add(line);
			map.put(variable, newAssignments);
		}
		else
		{
			SortedSet<Integer> assignments = new TreeSet<Integer>();
			assignments.add(line);
			map.put(variable, assignments);
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
