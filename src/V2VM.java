import cs132.util.ProblemException;
import cs132.vapor.parser.VaporParser;
import cs132.vapor.ast.VDataSegment;
import cs132.vapor.ast.VFunction;
import cs132.vapor.ast.VInstr;
import cs132.vapor.ast.VOperand;
import cs132.vapor.ast.VVarRef.Local;
import cs132.vapor.ast.VaporProgram;
import cs132.vapor.ast.VBuiltIn.Op;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

public class V2VM {
	
	public static VaporProgram parseVapor(InputStream in, PrintStream err) throws IOException
	{
		Op[] ops = {Op.Add, Op.Sub, Op.MulS, Op.Eq, Op.Lt, Op.LtS,
				Op.PrintIntS, Op.HeapAllocZ, Op.Error};
		boolean allowLocals = true;
		String[] registers = null;
		boolean allowStack = false;
		
		VaporProgram program;
		try {
			program = VaporParser.run(new InputStreamReader(in), 1, 1,
					java.util.Arrays.asList(ops),
					allowLocals, registers, allowStack);
			} catch (ProblemException ex) {
				err.println(ex.getMessage());
				return null;
			}
		
		return program;
	}
	
	public static void main2(String args[])
	{
		InputStream inputStream;
		try {
			inputStream = new FileInputStream("./vapor/BinaryTree.vapor");
			PrintStream errorStream = System.err;
			FileOutputStream regAllocStream = new FileOutputStream("regAlloc.txt");
			VaporProgram program = parseVapor(inputStream, errorStream);
			
			String data = translateData(program.dataSegments);
			String text = translateFunctions(program.functions, regAllocStream);
			
			System.out.print(data);
			System.out.print(text);
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public static void main(String args[])
	{
		InputStream inputStream;
		try {
			inputStream = System.in;
			PrintStream errorStream = System.err;
			VaporProgram program = parseVapor(inputStream, errorStream);
			
			String data = translateData(program.dataSegments);
			String text = translateFunctions(program.functions, null);
			
			System.out.print(data);
			System.out.print(text);
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
private static String translateFunctions(VFunction[] functions, FileOutputStream regAllocStream) {
		
		FlowVisitor flowVisitor = new FlowVisitor();
		SecondPassVisitor secondPass = new SecondPassVisitor();
		String code = "";
		
		// First determine how big the in, out, and local arrays need to be for each function
		// In determined from parameters
		// Out determined by the call with the most parameters
		// Local determined by how many callee save regs are used. Leaf funcs can use caller save regs
		try {
			for (VFunction function : functions)
			{
				// Generate line -> instruction map
				Map<Integer, VInstr> lineInstructionMap = new HashMap<Integer, VInstr>();
				for (VInstr instruction : function.body)
				{
					lineInstructionMap.put(instruction.sourcePos.line, instruction);
				}
				
				// Generate control flow graph
				Map<Integer, FlowNode> flowNodeMap = new HashMap<Integer, FlowNode>();
				Map<Integer, Set<Integer>> lineToPrevLinesMap = new HashMap<Integer, Set<Integer>>();
				
				Map<String, SortedSet<Integer>> variableUses = new HashMap<String, SortedSet<Integer>>();
				Map<String, SortedSet<Integer>> variableAssignments = new HashMap<String, SortedSet<Integer>>();
				
				// Add parameters to variableAssignments map
				for (Local parameter : function.params)
				{
					FlowInput.updateMap(variableAssignments, parameter.ident, function.sourcePos.line);
				}
				
				// Create FlowNodes and update variable assignment and usage maps
				FlowInput flowInput = new FlowInput(function.labels, variableUses, variableAssignments);
				for (VInstr instruction : function.body)
				{
					FlowNode node = (FlowNode) instruction.accept(flowInput, flowVisitor);
					flowNodeMap.put(node.lineNumber, node);
					
					for (Integer nextLine : node.nextLines)
					{
						if (lineToPrevLinesMap.containsKey(nextLine))
						{
							Set<Integer> prevLines = lineToPrevLinesMap.get(nextLine);
							Set<Integer> newPrevLines = new HashSet<Integer>(prevLines);
							newPrevLines.add(node.lineNumber);
							lineToPrevLinesMap.put(nextLine, newPrevLines);
						}
						else
						{
							Set<Integer> newPrevLines = new HashSet<Integer>();
							newPrevLines.add(node.lineNumber);
							lineToPrevLinesMap.put(nextLine, newPrevLines);
						}
					}
				}
				
				// Set the prev Lines sets for the control flow graph nodes
				for (Integer line : flowNodeMap.keySet())
				{
					FlowNode node = flowNodeMap.get(line);
					Set<Integer> prevLines = lineToPrevLinesMap.get(line);
					if (prevLines == null)
					{
						prevLines = new HashSet<Integer>();
						prevLines.add(function.sourcePos.line);
					}
					FlowNode newNode = new FlowNode(line, node.nextLines, prevLines);
					flowNodeMap.put(line, newNode);
				}
				
				// Find min and max line numbers for liveness for each variable
				Map<String, Range> linearRanges = new HashMap<String, Range>();
				for (String variable : function.vars)
				{
					if (variableUses.containsKey(variable))
					{
						int begin = Integer.MAX_VALUE;
						int end = Integer.MIN_VALUE;
						// Track each usage back to an assignment
						for (Integer useLine : variableUses.get(variable))
						{
							Range range = findAssignmentForVariableUsage(variable, useLine, variableAssignments.get(variable), flowNodeMap);
							if (range.begin < begin)
								begin = range.begin;
							if (range.end > end)
								end = range.end;
						}
						
						for (Integer assignLine : variableAssignments.get(variable))
						{
							if (assignLine < begin)
								begin = assignLine;
							if (assignLine > end)
								end = assignLine;
						}
						
						Range varRange = new Range(variable, begin, end);
						linearRanges.put(variable, varRange);
					}
				}
				
				int stackIn = 0;
				if (function.params.length > 4)
					stackIn = function.params.length - 4;
				
				Map<String, String> variableToRegister = new HashMap<String, String>();
				int maxConcurrentlyLive = linearScan(linearRanges, flowInput.isLeaf, variableToRegister);
				int spills = 0;
				int calleeSaves = 0;
				if (flowInput.isLeaf && maxConcurrentlyLive > leafRegisters.length)
				{
					spills = maxConcurrentlyLive - leafRegisters.length;
				}
				else if (!flowInput.isLeaf)
				{
					calleeSaves = maxConcurrentlyLive;
					if (maxConcurrentlyLive > nonleafRegisters.length)
					{
						calleeSaves = nonleafRegisters.length;
						spills = maxConcurrentlyLive - nonleafRegisters.length;
					}
				}
				int stackLocal = calleeSaves + spills; // no callerSaves, only leaf funcs use t registers
				
				int stackOut = flowInput.largestOut;
				
				code = code + "func " + function.ident + " [in " + String.valueOf(stackIn) + ", out " 
				+ String.valueOf(stackOut) + ", local " + String.valueOf(stackLocal) + "]\n";
				
				// debugging purposes
				if (regAllocStream != null)
				{
					String functionHeader = "func " + function.ident + " [in " + String.valueOf(stackIn) + ", out " 
							+ String.valueOf(stackOut) + ", local " + String.valueOf(stackLocal) + "]\n";
					regAllocStream.write(functionHeader.getBytes());
					
					String linearRangesOutput = "";
					for (String variable : linearRanges.keySet())
					{
						Range range = linearRanges.get(variable);
						linearRangesOutput = linearRangesOutput + "  " + variable + ": " + Integer.toString(range.begin) + "-" + Integer.toString(range.end) + "\n";
					}
					
					regAllocStream.write(linearRangesOutput.getBytes());
					regAllocStream.write("\n".getBytes());
				}
				
				
				// Save $s_ registers if necessary
				if (!flowInput.isLeaf)
				{
					for (int regIndex = 0; regIndex < calleeSaves; regIndex++)
					{
						code = code + "  local[" + Integer.toString(regIndex) + "] = $s" + Integer.toString(regIndex) + "\n";
					}
				}
				
				// Any numeric registers should be local array spills and need to be renamed
				for (String variable : variableToRegister.keySet())
				{
					String reg = variableToRegister.get(variable);
					if (isNumeric(reg))
					{
						reg = "local[" + Integer.toString(calleeSaves + Integer.valueOf(reg)) + "]";
						variableToRegister.put(variable, reg);
					}
				}
				
				// Assign any arguments (including arguments saved on the stack as the in array)
				for (int argIndex = 0; argIndex < function.params.length && argIndex < 4; argIndex++)
				{
					String parameter = function.params[argIndex].toString();
					String arg = variableToRegister.get(parameter);
					if (arg != null)
					{
						code = code + "  " + arg + " = $a" + Integer.toString(argIndex) + "\n";
					}
				}
				for (int argIndex = 4; argIndex < function.params.length; argIndex++)
				{
					String parameter = function.params[argIndex].toString();
					String arg = variableToRegister.get(parameter);
					
					if (arg != null)
					{
						String argReg = arg;
						String setupArg = "";
						if (arg.startsWith("l"))
						{
							setupArg = "  " + arg + " = $v1\n";
							argReg = "$v1";
						}
						
						code = code + "  " + argReg + " = in[" + Integer.toString(argIndex-4) + "]\n" + setupArg;
					}
				}
				
				// Second Pass, translate instructions
				InputSecondPass input2 = new InputSecondPass(variableToRegister);
				int labelIndex = 0;
				for (VInstr instruction : function.body)
				{
					while (labelIndex < function.labels.length && function.labels[labelIndex].sourcePos.line < instruction.sourcePos.line)
					{
						code = code + function.labels[labelIndex].ident + ":\n";
						labelIndex++;
					}
					
					if (instruction.equals(function.body[function.body.length-1]))
					{
						code = code + instruction.accept(input2, secondPass);
						
						// Restore $s_ registers if necessary
						if (!flowInput.isLeaf)
						{
							for (int regIndex = 0; regIndex < calleeSaves; regIndex++)
							{
								code = code + "  $s" + Integer.toString(regIndex) + " = local[" + Integer.toString(regIndex) + "]\n";
							}
						}
						
						code = code + "  ret\n";
					}
					else
						code = code + instruction.accept(input2, secondPass);
				}
				
				code = code + "\n";
			}
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return code;
	}

	private static Range findAssignmentForVariableUsage(String variable, Integer useLine, SortedSet<Integer> assignmentLines, Map<Integer, FlowNode> flowNodeMap) 
	{
		int currentLine = useLine;
		Range range = new Range(variable, currentLine, currentLine);
		Set<Integer> prevLines = flowNodeMap.get(currentLine).prevLines;
		Set<Integer> alreadyVisited = new HashSet<Integer>();
		alreadyVisited.add(currentLine);
		for (Integer prevLine : prevLines)
		{			
			int assignLine = recursivelyFindAssignment(prevLine, assignmentLines, alreadyVisited, flowNodeMap, range);
		}
		return range;
	}

	private static int recursivelyFindAssignment(Integer currentLine,
			SortedSet<Integer> assignmentLines, Set<Integer> alreadyVisited,
			Map<Integer, FlowNode> flowNodeMap, Range range) {
		int assignLine = 0;
		
		if (alreadyVisited.contains(currentLine))
			return 0;
		
		if (currentLine < range.begin)
			range.begin = currentLine;
		if (currentLine > range.end)
			range.end = currentLine;
		
		alreadyVisited.add(currentLine);
		
		if (assignmentLines.contains(currentLine))
			return currentLine;
		
		FlowNode node = flowNodeMap.get(currentLine);
		if (node == null)
			return 0;
		Set<Integer> prevLines = node.prevLines;
		if (prevLines == null)
			System.err.print("something went wrong");
		for (Integer prevLine : prevLines)
		{			
			assignLine = recursivelyFindAssignment(prevLine, assignmentLines, alreadyVisited, flowNodeMap, range);
		}
		return assignLine;
	}

	private static int linearScan(Map<String, Range> variableLives, boolean isLeaf, Map<String, String> variableToRegister) 
	{
		String[] registerArray = null;
		if (isLeaf)
			registerArray = leafRegisters;
		else
			registerArray = nonleafRegisters;
		SortedSet<String> availableRegisters = new TreeSet<String>(String.CASE_INSENSITIVE_ORDER);
		availableRegisters.addAll(Arrays.asList(registerArray));
		
		int maxVariablesConcurrentlyLive = 0;
		Range[] rangeArray = new Range[variableLives.values().size()];
		variableLives.values().toArray(rangeArray);
		Arrays.sort(rangeArray, Range.RangeComparator);
		Set<String> liveVariables = new HashSet<String>();
		int spills = 0;
		for (Range range : rangeArray)
		{
			int currentBeginning = range.begin;
			
			// First remove any live variables whose liveliness ended at or before this variables's range
			Set<String> variablesToRemove = new HashSet<String>();
			for (String variable : liveVariables)
			{
				int variableEnd = variableLives.get(variable).end;
				if (variableEnd <= currentBeginning)
					variablesToRemove.add(variable);
			}
			
			for (String variable : variablesToRemove)
			{
				liveVariables.remove(variable);
				availableRegisters.add(variableToRegister.get(variable));
			}
			
			// Add the current variable to the live set
			liveVariables.add(range.variableName);
			
			// Set max number of concurrently live variables if necessary
			if (liveVariables.size() > maxVariablesConcurrentlyLive)
				maxVariablesConcurrentlyLive = liveVariables.size();
			
			// Assign a register (or spill slot) to variable
			if (availableRegisters.isEmpty())
			{
				variableToRegister.put(range.variableName, Integer.toString(spills)); // Registers start with $, spills start with just the number
				spills++;
			}
			else
			{
				variableToRegister.put(range.variableName, availableRegisters.first());
				availableRegisters.remove(availableRegisters.first());
			}
		}
		
		return maxVariablesConcurrentlyLive;
	}

	private static String translateData(VDataSegment[] dataSegments) {
		String code = "";
		for (VDataSegment dataSegment : dataSegments)
		{
			code = code + (dataSegment.mutable ? "var " : "const ") + dataSegment.ident + "\n";
			for (VOperand.Static value : dataSegment.values)
			{
				code = code + "  " + value.toString() + "\n";
			}
			
			code = code + "\n";
		}
		return code;
	}
	
	private static boolean isNumeric(String str)  
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
	
	//private static String[] nonleafRegisters = {"$s0", "$s1"};
	//private static String[] leafRegisters = {"$t0", "$t1"};
	private static String[] leafRegisters = {"$t0", "$t1", "$t2", "$t3", "$t4", "$t5", "$t6", "$t7", "$t8"};
	private static String[] nonleafRegisters = {"$s0", "$s1", "$s2", "$s3", "$s4", "$s5", "$s6", "$s7"};
}
