package phylonet.coalescent;

import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;

import phylonet.coalescent.IClusterCollection.VertexPair;
import phylonet.tree.model.Tree;
import phylonet.tree.model.sti.STITreeCluster.Vertex;

public class WQComputeMinCostTaskConsumer extends AbstractComputeMinCostTask<Tripartition>{

	WQDataCollection wqDataCollection;
	//final byte getDoneState = 3;
	//final byte getOtherDoneState = 3;
	
	public WQComputeMinCostTaskConsumer(AbstractInference<Tripartition> inference, Vertex v) {
		super(inference, v);
		this.wqDataCollection = (WQDataCollection)inference.dataCollection;
	}
	
	protected double adjustWeight(long clusterLevelCost, Vertex smallV,
			Vertex bigv, Long Wdom) {	
		return Wdom;
	}
	
	@Override
	protected long scoreBaseCase(boolean rooted, List<Tree> trees) {	
		return 0l;
	}

	@Override
	protected AbstractComputeMinCostTask newMinCostTask( Vertex v) {
		return new WQComputeMinCostTaskConsumer(inference, v);
	}
	
	@Override
	protected long calculateClusterLevelCost() {
		return 0l;
	}

	@Override
	protected Tripartition STB2T(VertexPair vp) {
		return new Tripartition(vp.cluster1.getCluster(), vp.cluster2.getCluster(), vp.both.getCluster().complementaryCluster());
	}

	@Override
	Long defaultWeightForFullClusters() {
		return 0l;
	}
	
	long computeMinCost() throws CannotResolveException {
				
		// Already calculated. Don't re-calculate.
		if ( v._consDone ) {
			return v._max_score;
		}
		//
	
		int clusterSize = v.getCluster().getClusterSize();
	
		// SIA: base case for singelton clusters.
		if (clusterSize <= 1 || spm.isSingleSP(v.getCluster().getBitSet())) {
			
			v._max_score = scoreBaseCase(inference.isRooted(), inference.trees);

			v._consDone = true;
			
			return v._max_score;
		}
	
		LinkedBlockingQueue<VertexPair> clusterResolutions;
		try {
			clusterResolutions = (LinkedBlockingQueue<VertexPair>) this.inference.getQueueClusterResolutions().take();
		} catch (InterruptedException e1) {
			throw new RuntimeException(e1);
		}
	
		long clusterLevelCost = 0;

		Long [] weights  = new Long[clusterResolutions.size()];

		int j=0;
		for (VertexPair bi : clusterResolutions) {
			try {
				Long weight = null;
				if (clusterSize == GlobalMaps.taxonIdentifier.taxonCount()) {
					weight = defaultWeightForFullClusters();
				} else {
					Tripartition t = STB2T(bi);					
					weight =  inference.weightCalculator.getWeight(t);
				}					
				weights[j] = (weight);
				j++;
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}
		
		j = 0;
		for (VertexPair bi : clusterResolutions) {
			try {
	
				Vertex smallV = bi.cluster1;
				Vertex bigv = bi.cluster2;
	
				AbstractComputeMinCostTask<Tripartition> bigWork = newMinCostTask(bigv);
				AbstractComputeMinCostTask<Tripartition> smallWork = newMinCostTask(smallV);
	
				Long rscore = bigWork.compute();
				Long lscore = smallWork.compute();
	
				Long weight = weights[j];
				j++;
	
				if ((v._max_score != -1)
						&& (lscore + rscore + weight < v._max_score)) {
					continue;
				}
				if (lscore + rscore + weight == v._max_score && GlobalMaps.random.nextBoolean()) {
					continue;
				}
				v._max_score = (lscore + rscore + weight);
				v._min_lc = smallV;
				v._min_rc = bigv;
				//v._c = c;
	
			}
			catch (Exception e) {
				throw new RuntimeException(e);
			}
		}
	
		/**
		 * Should never happen
		 */
		if (v._min_lc == null || v._min_rc == null) {
			Logging.log("WARN: No Resolution found for ( "
					+ v.getCluster().getClusterSize() + " taxa ):\n"
					+ v.getCluster());
			throw new CannotResolveException(v.getCluster().toString());
		}
		
		v._consDone = (true);
	
		return v._max_score;
	}

}
