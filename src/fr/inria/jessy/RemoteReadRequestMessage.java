package fr.inria.jessy;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import net.sourceforge.fractal.multicast.MulticastMessage;
import net.sourceforge.fractal.utils.PerformanceProbe.TimeRecorder;
import fr.inria.jessy.store.JessyEntity;
import fr.inria.jessy.store.ReadRequest;

public class RemoteReadRequestMessage<E extends JessyEntity> extends MulticastMessage {

	private static final long serialVersionUID = ConstantPool.JESSY_MID;

	private ReadRequest<E> request;
	private static TimeRecorder unpackTime = new TimeRecorder("RemoteReadRequestMessage#unpackTime");
	
	// For Fractal
	public RemoteReadRequestMessage() {
	}

	public RemoteReadRequestMessage(ReadRequest<E> r) {
		super();
		request = r;
	}

	public ReadRequest<E> getReadRequest() {
		return request;
	}
	
	
	@Override
	@SuppressWarnings("unchecked")
	public void readExternal(ObjectInput in) throws ClassNotFoundException, IOException{
		super.readExternal(in);
		unpackTime.start();
		request = (ReadRequest<E>) in.readObject();
		unpackTime.stop();
	}
	
	@Override
	public void writeExternal(ObjectOutput out) throws IOException{
		super.writeExternal(out);
		out.writeObject(request);
	}
	
}