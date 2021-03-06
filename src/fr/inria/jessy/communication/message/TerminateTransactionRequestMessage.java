package fr.inria.jessy.communication.message;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Collection;

import net.sourceforge.fractal.wanamcast.WanAMCastMessage;
import fr.inria.jessy.ConstantPool;
import fr.inria.jessy.transaction.ExecutionHistory;

public class TerminateTransactionRequestMessage extends WanAMCastMessage{

	private static final long serialVersionUID = ConstantPool.JESSY_MID;

	public long startCastingTime;
	
	private Object computedObjectUponDelivery;
	
	// For Fractal
	public TerminateTransactionRequestMessage() {
	}

	public TerminateTransactionRequestMessage(ExecutionHistory eh,
			Collection<String> dest, String gSource, int source) {
		super(eh, dest, gSource, source);
		startCastingTime = System.currentTimeMillis();
	}

	public ExecutionHistory getExecutionHistory() {
		return (ExecutionHistory) serializable;
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException,
			ClassNotFoundException {
		super.readExternal(in);
		startCastingTime = in.readLong();
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		super.writeExternal(out);
		out.writeLong(startCastingTime);
	}

	@Override
	public boolean equals(Object obj) {
		TerminateTransactionRequestMessage input = (TerminateTransactionRequestMessage) obj;

		return this.getExecutionHistory().getTransactionHandler()
				.equals(input.getExecutionHistory().getTransactionHandler());
	}

	public Object getComputedObjectUponDelivery() {
		return computedObjectUponDelivery;
	}

	public void setComputedObjectUponDelivery(Object computedObjectUponDelivery) {
		this.computedObjectUponDelivery = computedObjectUponDelivery;
	}

	public String toStirng(){
		return serializable.toString();
	}
	
	
}
