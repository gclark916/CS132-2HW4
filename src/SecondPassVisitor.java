import cs132.vapor.ast.VAssign;
import cs132.vapor.ast.VBranch;
import cs132.vapor.ast.VBuiltIn;
import cs132.vapor.ast.VCall;
import cs132.vapor.ast.VGoto;
import cs132.vapor.ast.VInstr.VisitorPR;
import cs132.vapor.ast.VMemRead;
import cs132.vapor.ast.VMemWrite;
import cs132.vapor.ast.VReturn;


public class SecondPassVisitor extends VisitorPR<Object, Object, Exception> {

	@Override
	public Object visit(Object p, VAssign a) throws Exception {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object visit(Object p, VCall c) throws Exception {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object visit(Object p, VBuiltIn c) throws Exception {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object visit(Object p, VMemWrite w) throws Exception {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object visit(Object p, VMemRead r) throws Exception {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object visit(Object p, VBranch b) throws Exception {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object visit(Object p, VGoto g) throws Exception {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object visit(Object p, VReturn r) throws Exception {
		// TODO Auto-generated method stub
		return null;
	}

}
