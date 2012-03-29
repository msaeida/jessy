package fr.inria.jessy.benchmark.tpcc;

import fr.inria.jessy.Jessy;
import fr.inria.jessy.benchmark.tpcc.entities.*;
import fr.inria.jessy.transaction.ExecutionHistory;
import fr.inria.jessy.transaction.Transaction;

import java.io.*;
import java.util.*;


public class Delivery extends Transaction {
	
	private String W_ID;
	private String O_CARRIER_ID;

	public Delivery(Jessy jessy) throws Exception {
		super(jessy);
	}

	@Override
	public ExecutionHistory execute() {

    	try {
    		District district;
    		New_order no = null; 
    		Order order;
    		Order_line ol = null;
    		Customer customer;
    		int i,j,k;
    		
    		File log = new File("log file");
    		FileWriter fw = new FileWriter(log);

        	W_ID = "1";   /* warehouse number (W_ID) is constant */
        	
        	Random rand = new Random(System.currentTimeMillis());
        	
        	
        	/* for each of the 10 districts within the given warehouse */
        	for(i = 1; i<=10; i++) {
        		district = read(District.class, "D_W_"+ W_ID + "_" + "D_"+ i);
        		for(j=1;j<district.getD_NEXT_O();j++) {
        			no = read(New_order.class, "NO_W_"+ W_ID +"_"+ "NO_D_"+ i +"_"+ "NO_O_"+ j);
        			if(no != null)
        				j = district.getD_NEXT_O();   /* to skip */
        		}
        		
        		if(no == null) {   /* no result, skip */
        			/* record the result in the log file */
        			fw.write("no matching result in"+ i + "district\n");
        			continue;
        		}
        		/* save the NO_O_ID before delete the row */
        		int NO_O_ID = no.getNO_O_ID();
        		
        		/* Delete at New_Order */
        		no.setNO_W_ID(null);
        		no.setNO_D_ID(null);
        		no.setNO_O_ID(0);
        		write(no);
        		       		
        		order = read(Order.class, "O_W_"+W_ID + "_" + "O_D_"+ i + "_" + "O_"+NO_O_ID );
        		O_CARRIER_ID = Integer.toString(rand.nextInt(10 - 1) + 1);   /* The carrier number (O_CARRIER_ID) is randomly selected within [1 .. 10] */
        		order.setO_CARRIER_ID(O_CARRIER_ID);
        		/* Update Order */
        		write(order);
        		
        		
                int total_amount = 0;
        		for(k=1; k<=order.getO_OL_CNT();k++) {
        			ol = read(Order_line.class, "OL_W_"+W_ID + "_" + "OL_D_"+ i + "_" + "OL_O_"+ order.getO_ID() +"_" +"OL_"+k);  
            		ol.setOL_DELIVERY_D(new Date()); 
            		total_amount += ol.getOL_AMOUNT();
        		}
        		/* Update Order_line */
        		write(ol);
        		
        		customer = read(Customer.class, "C_W_"+W_ID + "_" + "C_D_"+ i + "_" + "C_"+ order.getO_C_ID());
        		customer.setC_BALANCE(customer.getC_BALANCE() + total_amount);
        		customer.setC_DELIVERY_CNT(customer.getC_DELIVERY_CNT()+1);
        		/* Update Customer */
        		write(customer);
        		
        		fw.close();
        		
        		return commitTransaction();	
        	}
        	
		} catch (Exception e) {
			e.printStackTrace();
		}
	
		return null;
	}

}