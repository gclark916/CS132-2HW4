import cs132.util.ProblemException;
import cs132.vapor.parser.VaporParser;
import cs132.vapor.ast.VDataSegment;
import cs132.vapor.ast.VFunction;
import cs132.vapor.ast.VInstr;
import cs132.vapor.ast.VOperand;
import cs132.vapor.ast.VVarRef;
import cs132.vapor.ast.VaporProgram;
import cs132.vapor.ast.VBuiltIn.Op;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
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
	
	public static void main(String args[])
	{
		InputStream inputStream;
		try {
			inputStream = new FileInputStream("./vapor/MoreThan4.vapor");
			PrintStream errorStream = System.err;
			VaporProgram program = parseVapor(inputStream, errorStream);
			
			String data = translateData(program.dataSegments);
			String text = translateFunctions(program.functions);
			
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
	
	public static void main2(String args[])
	{
		InputStream inputStream;
		try {
			inputStream = System.in;
			PrintStream errorStream = System.err;
			VaporProgram program = parseVapor(inputStream, errorStream);
			
			String data = translateData(program.dataSegments);
			String text = translateFunctions(program.functions);
			
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
	
private static String translateFunctions(VFunction[] functions) {
		
		FirstPassVisitor firstPass = new FirstPassVisitor();
		SecondPassVisitor secondPass = new SecondPassVisitor();
		String code = "";
		
		// First determine how big the in, out, and local arrays need to be for each function
		// In determined from parameters
		// Out determined by the call with the most parameters
		// Local determined by how many callee save regs are used. Leaf funcs can use caller save regs
		try {
			for (VFunction function : functions)
			{
				int stackIn = 0;
				if (function.params.length > 4)
					stackIn = function.params.length - 4;
				
				Map<String, Range> variableLives = new HashMap<String, Range>();
				for (VVarRef.Local parameter : function.params)
				{
					Range range = new Range(parameter.ident, parameter.sourcePos.line, parameter.sourcePos.line);
					variableLives.put(parameter.ident, range);
				}
				
				// First Pass, create liveness ranges
				Input input = new Input(0, variableLives);
				for (VInstr instruction : function.body)
				{
					instruction.accept(input, firstPass);
				}
				
				Map<String, String> variableToRegister = new HashMap<String, String>();
				int maxConcurrentlyLive = linearScan(input.variableLives, input.isLeaf, variableToRegister);
				int spills = 0;
				int calleeSaves = 0;
				if (input.isLeaf && maxConcurrentlyLive > 9)
				{
					spills = 9 - maxConcurrentlyLive;
				}
				else if (!input.isLeaf)
				{
					calleeSaves = maxConcurrentlyLive;
					if (maxConcurrentlyLive > 8)
					{
						calleeSaves = 8;
						spills = maxConcurrentlyLive - 8;
					}
				}
				int stackLocal = calleeSaves + spills; // no callerSaves, only leaf funcs use t registers
				
				int stackOut = input.largestOut;
				
				code = code + "func " + function.ident + " [in " + String.valueOf(stackIn) + ", out " 
				+ String.valueOf(stackOut) + ", local " + String.valueOf(stackLocal) + "]\n";
				
				// debugging purposes
				/*for (String variable : variableToRegister.keySet())
				{
					code = code + "  " + variable + ": " + variableToRegister.get(variable) + "\n";
				}*/
				// Save $s_ registers if necessary
				if (!input.isLeaf)
				{
					for (int regIndex = 0; regIndex < calleeSaves; regIndex++)
					{
						code = code + "  local[" + Integer.toString(regIndex) + "] = $s" + Integer.toString(regIndex) + "\n";
					}
				}
				
				// Assign any arguments (including arguments saved on the stack as the in array)
				for (int argIndex = 0; argIndex < function.params.length && argIndex < 4; argIndex++)
				{
					code = code + "  " + variableToRegister.get(function.params[argIndex].toString()) + " = $a" + Integer.toString(argIndex) + "\n";
				}
				for (int argIndex = 4; argIndex < function.params.length; argIndex++)
				{
					code = code + "  " + variableToRegister.get(function.params[argIndex].toString()) + " = in[" + Integer.toString(argIndex-4) + "]\n";
				}
				
				// Second Pass, translate instructions
				InputSecondPass input2 = new InputSecondPass(variableToRegister);
				for (VInstr instruction : function.body)
				{
					instruction.accept(input2, secondPass);
				}
				
				code = code + "\n";
			}
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return code;
	}

	private static int linearScan(Map<String, Range> variableLives, boolean isLeaf, Map<String, String> variableToRegister) 
	{
		String[] leafRegisters = {"$t0", "$t1", "$t2", "$t3", "$t4", "$t5", "$t6", "$t7", "$t8"};
		String[] nonleafRegisters = {"$s0", "$s1", "$s2", "$s3", "$s4", "$s5", "$s6", "$s7"};
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
}
