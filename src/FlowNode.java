import java.util.Set;


public class FlowNode {
	public int lineNumber;
	public Set<Integer> nextLines;
	public Set<Integer> prevLines;
	
	/**
	 * @param lineNumber
	 * @param nextLines
	 * @param prevLines
	 */
	public FlowNode(int lineNumber, Set<Integer> nextLines,
			Set<Integer> prevLines) {
		super();
		this.lineNumber = lineNumber;
		this.nextLines = nextLines;
		this.prevLines = prevLines;
	}
}
