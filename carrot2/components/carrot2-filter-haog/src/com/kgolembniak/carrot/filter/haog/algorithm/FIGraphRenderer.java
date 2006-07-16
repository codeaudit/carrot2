
/*
 * Carrot2 project.
 *
 * Copyright (C) 2002-2006, Dawid Weiss, Stanisław Osiński.
 * Portions (C) Contributors listed in "carrot2.CONTRIBUTORS" file.
 * All rights reserved.
 *
 * Refer to the full license file "carrot2.LICENSE"
 * in the root folder of the repository checkout or at:
 * http://www.carrot2.org/carrot2.LICENSE
 */

package com.kgolembniak.carrot.filter.haog.algorithm;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import com.dawidweiss.carrot.core.local.ProcessingException;
import com.dawidweiss.carrot.core.local.clustering.RawClusterBase;
import com.dawidweiss.carrot.core.local.clustering.RawClustersConsumer;
import com.dawidweiss.carrot.core.local.clustering.RawDocument;
import com.dawidweiss.carrot.core.local.clustering.TokenizedDocument;
import com.kgolembniak.carrot.filter.fi.FIParameters;
import com.kgolembniak.carrot.filter.fi.algorithm.Cluster;
import com.kgolembniak.carrot.filter.haog.measure.Statistics;

/**
 * This class renders {@link com.dawidweiss.carrot.core.local.clustering.RawCluster}
 * from vertices retrieved from Apriori based algorithm.
 * @see com.kgolembniak.carrot.filter.haog.algorithm.GraphRenderer
 * @author Karol Gołembniak
 */
public class FIGraphRenderer implements GraphRenderer {
	
	/**
	 * Documents retrieved from user's query
	 */
	private List documents;
	/**
	 * Parameters for clusters creation.
	 */
	private FIParameters params;
	/**
	 * Set used to check if document belongs to any cluster.
	 */
	private Set usedDocuments;
	/**
	 * 
	 */
	private RawClustersConsumer consumer;
	
	private GraphProcessor processor;
	
	private int repeatedClusters;
	
	public FIGraphRenderer(){
		processor = new GraphProcessor();
	}
	
	/**
     * This method creates output raw claster from internal results representation.
     * @throws ProcessingException 
     */
    public void renderRawClusters(List kernel, List documents, Object params,
   	RawClustersConsumer consumer) throws ProcessingException {
    	this.params = (FIParameters) params;
    	this.documents = documents;
    	this.consumer = consumer;
    	this.usedDocuments = new HashSet(documents.size());
    	this.repeatedClusters = 0;
    	
    	CombinedVertex kernelVertex;
		RawClusterBase rawCluster;
		//Reverse makes more complex cluster to be first on the list.
    	for (int i1=kernel.size()-1; i1>=0; i1--){
    		kernelVertex = (CombinedVertex) kernel.get(i1);
    		if (this.params.getHierarchyCreationWay()==0) {
        		rawCluster = createRCFromCVSimple(kernelVertex);
    		} else {
        		rawCluster = createRCFromCVFull(kernelVertex);
    		}
    		this.consumer.addCluster(rawCluster);
		}
        Statistics.getInstance().setValue("Clusters repeated in hierarhy", 
        		new Integer(this.repeatedClusters));

		rawCluster = new RawClusterBase();
    	rawCluster.addLabel("Other");
    	RawDocument rawDocument;
    	TokenizedDocument tokenizedDoc;
    	
    	HashSet docs = new HashSet(this.documents);
    	docs.removeAll(this.usedDocuments);
    	
    	Statistics.getInstance().setValue("Documents not grupped", new Integer(docs.size()));
    	for (Iterator it = docs.iterator(); it.hasNext();){
			tokenizedDoc = (TokenizedDocument) it.next();
	        rawDocument = (RawDocument) tokenizedDoc.getProperty(
	        		TokenizedDocument.PROPERTY_RAW_DOCUMENT);
        	rawCluster.addDocument(rawDocument);
    	}
    	
    	this.consumer.addCluster(rawCluster);
	}
    
    /**
     * This method converts internal algorithm representation of vertices
     * to clusters suitalbe for displaying later. It uses kernel discovering 
     * to decide to add cluster to hierarchy.
     * @param vertex
     * @return cluster
     */
    private RawClusterBase createRCFromCVFull(CombinedVertex vertex)
    throws ProcessingException {
		RawClusterBase rawCluster = new RawClusterBase();

		getDocumentsForCluster(vertex, rawCluster);		
		
    	List subGraph = vertex.getSuccList();
    	subGraph = processor.repairPredLists(subGraph);
    	List subgraphKernel = processor.findKernel(subGraph);
    	
    	CombinedVertex subVertex = null;
    	for (Iterator it = subgraphKernel.iterator(); it.hasNext();){
    		subVertex = (CombinedVertex) it.next();
    		if (subVertex.isUsed()) {
    			repeatedClusters ++;
    		} else {
    			subVertex.setUsed(true);
    		}
    		RawClusterBase subCluster = createRCFromCVFull(subVertex);
    		rawCluster.addSubcluster(subCluster);
    	}
    	
		return rawCluster;
	}

    /**
     * This method converts internal algorithm representation of vertices
     * to clusters suitalbe for displaying later. It uses simple grandchild 
     * criterion to decide to add cluster to hierarchy.
     * @param vertex
     * @return cluster
     */
	private RawClusterBase createRCFromCVSimple(CombinedVertex vertex) {
		RawClusterBase rawCluster = new RawClusterBase();

		getDocumentsForCluster(vertex, rawCluster);

		List successors = vertex.getSuccList();
		RawClusterBase subCluster;
		CombinedVertex successor;
		CombinedVertex sibling;
		
		boolean belongsToKernel;
		//for all children of this node
		for (int i1=0; i1<successors.size(); i1++){
			belongsToKernel = true;
			successor = (CombinedVertex) successors.get(i1);
			//Check if vertex isn't also grandchild =
			//Check if vertex belongs to kernel of subgraph
			for (int i2=0; i2<successors.size(); i2++) {
				sibling = (CombinedVertex) successors.get(i2); 
				if (sibling.getSuccList().contains(successor)){
					belongsToKernel = false;
				}
			}
			
			if (belongsToKernel){
	    		if (successor.isUsed()) {
	    			repeatedClusters ++;
	    		} else {
	    			successor.setUsed(true);
	    		}
				subCluster = createRCFromCVSimple(successor);
				rawCluster.addSubcluster(subCluster);
			}
		}
		return rawCluster;
	}

	/**
	 * This method adds documents contained in given vertex to given cluster.
	 * @param vertex - Vertex containing documents
	 * @param rawCluster - Cluster to add documents to.
	 */
	private void getDocumentsForCluster(CombinedVertex vertex, RawClusterBase rawCluster) {
		RawDocument rawDocument;
		TokenizedDocument tokenizedDoc;		
		rawCluster.addLabel(vertex.getVertexDescription(params));
		
		List vertices = vertex.getVertices();
		Vertex childVertex;
		HashSet docSet = new HashSet();
		for (int i1=0; i1<vertices.size(); i1++){
			childVertex = (Vertex) vertices.get(i1);
			Cluster cluster = (Cluster) childVertex.getRepresentedCluster();
			docSet.addAll(cluster.getDocuments());
		}
		
		//for all documents in node(cluster)
		for (Iterator doc = docSet.iterator(); doc.hasNext();){
			tokenizedDoc = (TokenizedDocument) doc.next();
			this.usedDocuments.add(tokenizedDoc);
	        rawDocument = (RawDocument) tokenizedDoc.getProperty(
	        		TokenizedDocument.PROPERTY_RAW_DOCUMENT);
        	rawCluster.addDocument(rawDocument);
		}
	}
	
}
