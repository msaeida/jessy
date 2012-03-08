package fr.inria.jessy.benchmark.tpcc;

import fr.inria.jessy.Jessy;
import fr.inria.jessy.benchmark.tpcc.entities.*;
import fr.inria.jessy.transaction.*;

import java.util.*;

public class InsertData extends Transaction {
	
	public InsertData(Jessy jessy) throws Exception{
		super(jessy);
	}

	@Override
	public ExecutionHistory execute() {
		try {
			int i, j, k;
			Random rand = new Random(System.currentTimeMillis());			
			Warehouse wh;			
			District dis;			
			Customer cus;			
			Item it;
			
			/*for i  warehouses*/
			for(i=0; i<1; i++){			
				wh = new Warehouse("W_"+i);
				wh.setW_ID(Integer.toString(i));
				wh.setW_NAME("Warehouse"+i);
				wh.setW_TAX((int) rand.nextFloat());
				wh.setW_YTD((int) rand.nextFloat());

				write(wh);
				
				/*each warehouse has 10 district*/
				for(j=0; i<10; i++){
					dis = new District("D_W_"+i+"_D_"+j);
					dis.setD_ID(Integer.toString(j));
					dis.setD_W_ID(wh.getW_ID());
					dis.setD_NAME("District"+j);
					dis.setD_TAX((int) rand.nextFloat());
					dis.setD_YTD((int) rand.nextFloat());
					dis.setD_NEXT_O(1);

					write(dis);
					
					/*each district has 3k customer*/
					for(k=0; k<3000; k++){
						cus=new Customer("C_W_"+i+"_C_D_"+j+"_C_"+k);
						cus.setC_ID(Integer.toString(k));
						cus.setC_D_ID(dis.getD_ID());
						cus.setC_W_ID(wh.getW_ID());

						write(cus);
						
						
					}
				}
					
			}
			
			/*for whole system, we have 10k different items*/
			/*10k items*/
			for(i=0; i<10000; i++){
				it = new Item("I_"+i);
				it.setI_ID(Integer.toString(i));
				it.setI_IM_ID(Integer.toString(i));
				it.setI_NAME("item"+i);
				it.setI_PRICE(rand.nextInt(5000 - 1) + 1);
				
				write(it);
			}
			
			return commitTransaction();			
		} catch (Exception ex) {
			ex.printStackTrace();
			return null;
		}		
	}

}
