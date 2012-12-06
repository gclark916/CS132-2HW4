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
import java.util.HashMap;
import java.util.Map;

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
			inputStream = new FileInputStream("./vapor/BubbleSort.vapor");
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
					Range range = new Range(parameter.sourcePos.line, parameter.sourcePos.line);
					variableLives.put(parameter.ident, range);
				}
				
				Input input = new Input(0, variableLives);
				for (VInstr instruction : function.body)
				{
					instruction.accept(input, firstPass);
				}
				
				int stackOut = input.largestOut;
			}
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return code;
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
