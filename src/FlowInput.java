import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;

import cs132.vapor.ast.VCodeLabel;


public class FlowInput {

	public VCodeLabel[] labels;
	public Map<String, SortedSet<Integer>> variableUses;
	public Map<String, SortedSet<Integer>> variableAssignments;
	public boolean isLeaf;
	public int largestOut;
	
	public FlowInput(VCodeLabel[] labels, Map<String, SortedSet<Integer>> variableUses, Map<String, SortedSet<Integer>> variableAssignments) {
		super();
		this.labels = labels;
		this.variableUses = variableUses;
		this.variableAssignments = variableAssignments;
		this.isLeaf = true;
		this.largestOut = 0;
	}
	
	public static void updateMap(Map<String, SortedSet<Integer>> map, String variable, int line)
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
	
}
