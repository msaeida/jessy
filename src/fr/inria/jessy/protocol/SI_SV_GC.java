package fr.inria.jessy.protocol;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.log4j.Logger;

import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap;

import fr.inria.jessy.ConstantPool;
import fr.inria.jessy.ConstantPool.ATOMIC_COMMIT_TYPE;
import fr.inria.jessy.communication.JessyGroupManager;
import fr.inria.jessy.communication.message.TerminateTransactionRequestMessage;
import fr.inria.jessy.consistency.Consistency;
import fr.inria.jessy.consistency.SI;
import fr.inria.jessy.store.DataStore;
import fr.inria.jessy.store.JessyEntity;
import fr.inria.jessy.store.ReadRequest;
import fr.inria.jessy.transaction.ExecutionHistory;
import fr.inria.jessy.transaction.ExecutionHistory.TransactionType;
import fr.inria.jessy.transaction.TransactionHandler;
import fr.inria.jessy.transaction.termination.vote.Vote;
import fr.inria.jessy.transaction.termination.vote.VotingQuorum;
import fr.inria.jessy.vector.ScalarVector;
import fr.inria.jessy.vector.Vector;

/**
 * This implementation is very similar to [Serrano'07] with the following difference:
 * In Serrano there is no voting phase because each replica holds the last committed version of all objects, and can decide locally. 
 *
 * CONS: SI
 * Vector: Scalar Vector
 * Atomic Commitment: Group Communication (Atomic Multicast)
 * 
 * @author Masoud Saeida Ardekani
 *
 */
public class SI_SV_GC extends SI {
	private static Logger logger = Logger
			.getLogger(SI_SV_GC.class);

	static{
		READ_KEYS_REQUIRED_FOR_COMMUTATIVITY_TEST=false;
		ConstantPool.PROTOCOL_ATOMIC_COMMIT=ATOMIC_COMMIT_TYPE.ATOMIC_MULTICAST;
	}
	
	public SI_SV_GC(JessyGroupManager m, DataStore store) {
		super(m, store);
		Consistency.SEND_READSET_DURING_TERMINATION=false;
	}

	/**
	 * {@inheritDoc}
	 */
	@SuppressWarnings("unchecked")
	@Override
	public boolean certify(ExecutionHistory executionHistory) {

		TransactionType transactionType = executionHistory.getTransactionType();

		/*
		 * if the transaction is a read-only transaction, it commits right away.
		 */
		if (transactionType == TransactionType.READONLY_TRANSACTION) {
			if (ConstantPool.logging)
				logger.debug(executionHistory.getTransactionHandler() + " >> "
						+ transactionType.toString() + " >> COMMITTED");
			return true;
		}

		/*
		 * if the transaction is an initialization transaction, it first
		 * increments the vectors and then commits.
		 */
		if (transactionType == TransactionType.INIT_TRANSACTION) {

			return true;
		}

		/*
		 * If the transaction is not read-only or init, we consider the create
		 * operations as update operations. Thus, we move them to the writeSet
		 * List.
		 */
		if (executionHistory.getCreateSet()!=null)
			executionHistory.getWriteSet().addEntity(executionHistory.getCreateSet());

		JessyEntity lastComittedEntity;
		for (JessyEntity tmp : executionHistory.getWriteSet().getEntities()) {

			if (!manager.getPartitioner().isLocal(tmp.getKey()))
				continue;
			
			try {

				lastComittedEntity = store
						.get(new ReadRequest<JessyEntity>(
								(Class<JessyEntity>) tmp.getClass(),
								"secondaryKey", tmp.getKey(), null))
						.getEntity().iterator().next();

				if (lastComittedEntity.getLocalVector().isCompatible(
						tmp.getLocalVector()) != Vector.CompatibleResult.COMPATIBLE) {

					if (ConstantPool.logging)
						logger.debug("lastCommitted: "
								+ lastComittedEntity.getLocalVector() + " tmp: "
								+ tmp.getLocalVector());

					return false;
				}
			} catch (NullPointerException e) {
				// nothing to do.
				// the key is simply not there.
			}

		}

		if (ConstantPool.logging)
			logger.debug(executionHistory.getTransactionHandler() + " >> "
					+ transactionType.toString() + " >> COMMITTED");

		return true;
	}
	
	@Override
	public boolean applyingTransactionCommute() {
		return false;
	}
	
	@Override
	public boolean transactionDeliveredForTermination(ConcurrentLinkedHashMap<UUID, Object> terminatedTransactions, ConcurrentHashMap<TransactionHandler, VotingQuorum>  quorumes, TerminateTransactionRequestMessage msg){
		try{
			int newVersion;
			// WARNING: there is a cast to ScalarVector
			if (msg.getExecutionHistory().getTransactionType() != TransactionType.INIT_TRANSACTION) {
				newVersion = ScalarVector.incrementAndGetLastCommittedSeqNumber();
			} else {
				newVersion = 0;
			}
			
			ScalarVector.committingTransactionSeqNumber.offer(newVersion);
			msg.setComputedObjectUponDelivery(newVersion);
		}
		catch (Exception ex){
			ex.printStackTrace();
		}
		
		Set<String> voteSenders =manager.getPartitioner().resolveNames(getConcerningKeys(
				msg.getExecutionHistory(),
				ConcernedKeysTarget.SEND_VOTES)); 

		if (voteSenders.contains(manager.getMyGroup().name())){
			return true;
		}
		else{
			nonConcernedMessages.put(msg.getExecutionHistory().getTransactionHandler().getId(), msg);

			try{
				if (quorumes.containsKey(msg.getExecutionHistory().getTransactionHandler())){
					voteAdded(msg.getExecutionHistory().getTransactionHandler(),terminatedTransactions, quorumes);
				}

			}
			catch(Exception ex){
				ex.printStackTrace();
			}
			
			
			return false;
		}
	}

	/**
	 * update (1) the {@link lastCommittedTransactionSeqNumber}: incremented by
	 * 1 (2) the {@link committedWritesets} with the new
	 * {@link lastCommittedTransactionSeqNumber} and {@link committedWritesets}
	 * (3) the scalar vector of all updated or created entities
	 */
	@SuppressWarnings("rawtypes")
	@Override
	public void prepareToCommit(TerminateTransactionRequestMessage msg) {
		ExecutionHistory executionHistory=msg.getExecutionHistory();

		int newVersion=(Integer)msg.getComputedObjectUponDelivery();		
		// WARNING: there is a cast to ScalarVector
		if (executionHistory.getTransactionType() != TransactionType.INIT_TRANSACTION) {

			for (JessyEntity je : executionHistory.getWriteSet().getEntities()) {
				((ScalarVector) je.getLocalVector()).update(newVersion);
			}

		} else {
			for (JessyEntity je : executionHistory.getCreateSet().getEntities()) {
				((ScalarVector) je.getLocalVector()).update(newVersion);
			}
		}

		ScalarVector.removeCommittingTransactionSeqNumber(newVersion);
		
		if (ConstantPool.logging)
			logger.debug(executionHistory.getTransactionHandler() + " >> "
					+ "COMMITED, lastCommittedTransactionSeqNumber:"
					+ ScalarVector.getLastCommittedSeqNumber());
	}
	
	public void postAbort(TerminateTransactionRequestMessage msg, Vote Vote){
		ScalarVector.removeCommittingTransactionSeqNumber((Integer)msg.getComputedObjectUponDelivery());
	}

	@Override
	public Set<String> getVotersToJessyProxy(
			Set<String> termincationRequestReceivers,
			ExecutionHistory executionHistory) {
		
		Set<String> keys = new HashSet<String>();
		keys.addAll(executionHistory.getWriteSet().getKeys());
		keys.addAll(executionHistory.getCreateSet().getKeys());

		Set<String> destGroups = new HashSet<String>();
		
		destGroups
		.addAll(manager.getPartitioner().resolveNames(keys));

		
		return destGroups;
	}
	
	ConcurrentHashMap<UUID, TerminateTransactionRequestMessage> nonConcernedMessages=new ConcurrentHashMap<UUID, TerminateTransactionRequestMessage>();
	Object dummyObject=new Object();
	
	@Override
	public void voteAdded(TransactionHandler th, ConcurrentLinkedHashMap<UUID, Object> terminatedTransactions, ConcurrentHashMap<TransactionHandler, VotingQuorum>  quorumes) {
		try{

			if (manager.isProxy()){
				return ;
			}

			if (!nonConcernedMessages.containsKey(th.getId())){
				return;
			}
			synchronized (nonConcernedMessages){
				if (!nonConcernedMessages.containsKey(th.getId())){
					return;
				}
			
				if (quorumes.containsKey(th)){			

					if (quorumes.get(th).getReceivedVoters()==null)
						return;


					int receivedVotes= quorumes.get(th).getReceivedVoters().size();

					TerminateTransactionRequestMessage msg=nonConcernedMessages.get(th.getId());
					int waitingVotes =manager.getPartitioner().resolveNames(getConcerningKeys(
							msg.getExecutionHistory(),
							ConcernedKeysTarget.SEND_VOTES)).size();

					if (receivedVotes>=waitingVotes){
						ScalarVector.removeCommittingTransactionSeqNumber((Integer)msg.getComputedObjectUponDelivery());
						nonConcernedMessages.remove(th.getId());
						quorumes.remove(th);
						terminatedTransactions.put(th.getId(), dummyObject);
					}
				}
			}
		}
		catch (Exception ex){
			ex.printStackTrace();
		}
	}
	
}