import java.util.Comparator;

public class Range {
	String variableName;
	public int begin;
	public int end;
	
	/**
	 * @param begin
	 * @param end
	 */
	public Range(String variableName, int begin, int end) {
		super();
		this.variableName = variableName;
		this.begin = begin;
		this.end = end;
	}
	
	public static Comparator<Range> RangeComparator = new Comparator<Range>() {
		public int compare(Range r1, Range r2)
		{
			int ret = Integer.compare(r1.begin, r2.begin);
			if (ret == 0)
				ret = Integer.compare(r1.end, r2.end);
			
			return ret;
		}
	};
}
