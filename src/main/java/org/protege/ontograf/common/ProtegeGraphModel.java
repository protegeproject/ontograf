package org.protege.ontograf.common;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.regex.Pattern;

import javax.swing.Icon;

import org.protege.editor.owl.OWLEditorKit;
import org.protege.editor.owl.model.OWLModelManager;
import org.protege.ontograf.common.util.OWLIconProviderImpl;
import org.semanticweb.owlapi.model.AxiomType;
import org.semanticweb.owlapi.model.ClassExpressionType;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLClassExpression;
import org.semanticweb.owlapi.model.OWLEntity;
import org.semanticweb.owlapi.model.OWLIndividual;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLObjectProperty;
import org.semanticweb.owlapi.model.OWLObjectPropertyExpression;
import org.semanticweb.owlapi.model.OWLObjectSomeValuesFrom;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLQuantifiedRestriction;
import org.semanticweb.owlapi.model.OWLSubClassOfAxiom;
import org.semanticweb.owlapi.search.EntitySearcher;

import ca.uvic.cs.chisel.cajun.filter.FilterManager;
import ca.uvic.cs.chisel.cajun.graph.DefaultGraphModel;
import ca.uvic.cs.chisel.cajun.graph.arc.DefaultGraphArc;
import ca.uvic.cs.chisel.cajun.graph.arc.GraphArc;
import ca.uvic.cs.chisel.cajun.graph.node.DefaultGraphNode;
import ca.uvic.cs.chisel.cajun.graph.node.GraphNode;

/**
 * Graph model representation of Protege OWL API. Converts OWL relationships and entities
 * into GraphArc's and GraphNode's.
 * 
 * @author seanf
 */
public class ProtegeGraphModel extends DefaultGraphModel {
	/** Protege specific relationships */
	protected static final String DIRECT_SUBCLASS_SLOT_TYPE = "has subclass";
	protected static final String DIRECT_INDIVIDUAL_SLOT_TYPE = "has individual";
	protected static final String SUFFIX_DOMAIN_RANGE = " (Domain>Range)";
	protected static final String SUB_CLASS_SOME_VALUE_OF = "(Subclass some)";
	protected static final String SUB_CLASS_ALL_VALUES = "(Subclass all)";
	protected static final String EQUIVALENT_CLASS_SOME_VALUE_OF = "(Equivalent class some)";
	protected static final String EQUIVALENT_CLASS_ALL_VALUES = "(Equivalent class all)";

	/** Protege specific node types */
	protected static final String UNKNOWN_ART_TYPE = "unknown";
	protected static final String CLASS_ART_TYPE = "class";
	protected static final String INDIVIDUAL_ART_TYPE = "individual";

	/** used to restrict an expansion to a specific arc type */
	protected String restrictToArcType;
	
	/** map to store the number of arcs associated with a given frame object */
	private Map<OWLEntity, Set<GraphArc>> frameToArcCount;
	
	/** collections for caching frame to arc relationships based on reified relationships */
	private Map<OWLNamedIndividual, Set<GraphArc>> artifactToUnreifiedRels;
	
	/** set of domain/range constraints for the OWL file */
	private Set<GraphArc> domainRangeRelsBuffer;
	
	/** current set of arc types available */
	private Collection<Object> arcTypes;
	
	private OWLOntology owlOntology;
	private OWLModelManager owlModelManager;
	private OWLEditorKit owlEditorKit;
	
	private FilterManager filterManager;
	
	public ProtegeGraphModel(OWLEditorKit owlEditorKit) {
		super();
		
		owlModelManager = owlEditorKit.getModelManager();
		this.owlEditorKit = owlEditorKit;
		owlOntology = owlModelManager.getActiveOntology();
		
		frameToArcCount = new HashMap<OWLEntity, Set<GraphArc>>();
	}
	
	public OWLModelManager getOwlModelManager() {
		return owlModelManager;
	}
	
	/**
	 * Adds the entity as a node and adds any arcs that exist between this new node and the
	 * existing nodes on the canvas.
	 * 
	 * @param entity
	 */
	public void show(OWLEntity entity, FilterManager filterManager) {
		this.filterManager = filterManager;
		
		List<GraphArc> arcs = new ArrayList<GraphArc>();
		
		addNode(entity);
		
		arcs.addAll(createIncomingRelationships(entity, true));
		arcs.addAll(createOutgoingRelationships(entity, true));

		addArcsToModel(arcs, false);
		recalculateArcStyles();
	}

	/**
	 * Shows the neighborhood for a given Frame.
	 * 
	 * @param entity The entity object to get the neighborhood for.
	 * @param removeOldNodes True if current visible nodes should be removed.
	 */
	public void showNeighborhood(OWLEntity entity, boolean removeOldNodes) {
		List<OWLEntity> singleItemList = new ArrayList<OWLEntity>(1);
		singleItemList.add(entity);

		showNeighborhood(singleItemList, removeOldNodes);
	}
	
	/**
	 * Shows the neighborhood for a collection of Frame objects.
	 * 
	 * @param nodes The collection of OWLEntity objects to get the neighborhood for.
	 * @param removeOldNodes True if current visible nodes should be removed.
	 */
	public void showNeighborhood(Collection<? extends OWLEntity> nodes, boolean removeOldNodes) {
		List<GraphArc> arcs = new ArrayList<GraphArc>();
		for (OWLEntity entity : nodes) {
			arcs.addAll(createIncomingRelationships(entity, false));
			arcs.addAll(createOutgoingRelationships(entity, false));
		}

		addArcsToModel(arcs, removeOldNodes);
		recalculateArcStyles();
	}
	
	/**
	 * Adds the list of arcs to the graph model. All nodes included in the arc relationships are
	 * also added to the model if necessary.
	 * 
	 * @param arcs
	 */
	private void addArcsToModel(Collection<GraphArc> arcs, boolean removeOldNodes) {
		Set<GraphNode> newGraphNodes = new HashSet<GraphNode>();

		for (GraphArc arc : arcs) {
			GraphArc createdArc = addArc((OWLEntity) arc.getSource().getUserObject(), (OWLEntity) arc.getDestination().getUserObject(), arc.getType().toString(), arc.getIcon());
			if (createdArc != null) {
				newGraphNodes.add(createdArc.getSource());
				newGraphNodes.add(createdArc.getDestination());
				createdArc.setInverted(arc.isInverted());
				
				//PNode n = (PNode)createdArc.getDestination();
				//System.out.println(n.getRoot().getClass());
			}
		}

		if (removeOldNodes) {
			// remove all old nodes
			GraphNode[] allNodes = getAllNodes().toArray(new GraphNode[getAllNodes().size()]);
			for (GraphNode node : allNodes) {
				if (!newGraphNodes.contains(node)) {
					removeNode(node.getUserObject());
				}
			}
		}
	}
	
	/**
	 * Adds an OWLEntity object as a GraphNode to the model.
	 * 
	 * @return The newly created GraphNode.
	 */
	protected GraphNode addNode(OWLEntity entity) {
		OWLIconProviderImpl iconProvider = new OWLIconProviderImpl(owlModelManager);
		Icon icon = iconProvider.getIcon(entity);
		return addNode(entity, owlModelManager.getRendering(entity), icon, getNodeType(entity));
	}
	
	/**
	 * Gets the node type as a string based on the OWLEntity object.
	 * 
	 * @return The node type as a string.
	 */
	protected String getNodeType(OWLEntity entity) {
		if(entity instanceof OWLClass) {
            return CLASS_ART_TYPE;
        } else if(entity instanceof OWLIndividual) {
            return INDIVIDUAL_ART_TYPE;
        }
		return UNKNOWN_ART_TYPE;
	}
	
	/**
	 * Adds a GraphArc to the model if it doesn't already exist.
	 * 
	 * @return The created or found GraphArc.
	 */
	protected GraphArc addArc(OWLEntity srcEntity, OWLEntity destEntity, String relType, Icon icon) {
		if (!relType.contains(restrictToArcType)) {
			return null;
		}

		boolean newNode = true;
		if (getNode(destEntity) != null) {
			newNode = false;
		}

		GraphNode srcNode = addNode(srcEntity);
		GraphNode destNode = addNode(destEntity);

		if (newNode) {
			destNode.setLocation(srcNode.getBounds().getX(), srcNode.getBounds().getY());
		}

		String key = srcEntity.toString() + relType + destEntity.toString();
		DefaultGraphArc arc = (DefaultGraphArc) addArc(key, srcNode, destNode, relType, icon);
		//arc.setType(relType);
		// for this sample the arc types work backworks (is_a, etc)
		arc.setInverted(true);
		return arc;
	}
	
	/**
	 * Determines if the given node is displayable, meaning that it if mustBeVisible is true, then
	 * the entity must exist in the graph model.
	 * 
	 * @return True if this node should be displayed.
	 */
	protected boolean isDisplayableNode(OWLEntity entity, boolean mustBeVisible) {
		return !mustBeVisible || mustBeVisible && getNode(entity) != null;
	}
	
	/**
	 * Creates all incoming relationships for the entityOfInterest.
	 * 
	 * @param entityOfInterest
	 * @param mustBeVisible True if we only want to create relationships between existing nodes.
	 * @return The list of GraphArcs created.
	 */
	protected Set<GraphArc> createIncomingRelationships(OWLEntity entityOfInterest, boolean mustBeVisible) {
		Set<GraphArc> incomingArcs = new HashSet<GraphArc>();

		incomingArcs.addAll(loadParents(entityOfInterest, mustBeVisible));
		incomingArcs.addAll(loadDomainRangeRels(entityOfInterest, false, mustBeVisible));
		incomingArcs.addAll(findIncomingIndividualRelationships(entityOfInterest, mustBeVisible));
		incomingArcs.addAll(loadUnreifiedRelations(entityOfInterest, mustBeVisible));
		incomingArcs.addAll(findIncomingConditionsRelationships(entityOfInterest, mustBeVisible));
		
		return incomingArcs;
	}
	
	/**
	 * Creates all outgoing relationships for a given OWLEntity.
	 * 
	 * @param entityOfInterest
	 * @param mustBeVisible True if we only want to create relationships between existing nodes.
	 * @return The list of GraphArcs created.
	 */
	protected Set<GraphArc> createOutgoingRelationships(OWLEntity entityOfInterest, boolean mustBeVisible) {
		Set<GraphArc> outgoingRels = new HashSet<GraphArc>();

		outgoingRels.addAll(loadChildren(entityOfInterest, mustBeVisible));
		outgoingRels.addAll(loadDomainRangeRels(entityOfInterest, true, mustBeVisible));
		outgoingRels.addAll(findOutgoingIndividualRelationships(entityOfInterest, mustBeVisible));
		outgoingRels.addAll(findOutgoingConditionsRelationships(entityOfInterest, mustBeVisible));

		return outgoingRels;
	}
	
	/**
	 * Finds relationships between an artifact as a item in the domain, and the ranges of its
	 * properties
	 */
	private Set<GraphArc> loadDomainRangeRels(OWLEntity entityOfInterest, boolean outgoing, boolean mustBeVisible) {
		Set<GraphArc> domainRangeArcs = new HashSet<GraphArc>();
		
		getDomainRangeRelationships(); // ensures that domain range rels are created
		for (GraphArc relationship : domainRangeRelsBuffer) {
			if(!filterManager.isArcTypeVisible(relationship.getType())) {
                continue;
            }
			
			OWLEntity sourceObject = (OWLEntity) relationship.getSource().getUserObject();
			OWLEntity destObject = (OWLEntity) relationship.getDestination().getUserObject();

			if (!isDisplayableNode(sourceObject, mustBeVisible) || !isDisplayableNode(destObject, mustBeVisible)) {
				continue;
			}
			if (outgoing && sourceObject.equals(entityOfInterest) || destObject.equals(entityOfInterest)) {
				if(outgoing) {
                    relationship.setInverted(false);
                }
				
				domainRangeArcs.add(relationship);
			}
			
		}

		return domainRangeArcs;
	}
	
	private Set<GraphArc> loadUnreifiedRelations(OWLEntity entityOfInterest, boolean mustBeVisible) {
		unreifyRelationInstances();
		
		Set<GraphArc> unreifiedRels = artifactToUnreifiedRels.get(entityOfInterest);
		if(unreifiedRels != null) {
			for(GraphArc arc : unreifiedRels) {
				if(!filterManager.isArcTypeVisible(arc.getType())) {
					unreifiedRels.remove(arc);
				}
				else {
					if(!isDisplayableNode((OWLEntity)arc.getDestination().getUserObject(), mustBeVisible)) {
						unreifiedRels.remove(arc);
					}
				}
			}
		}
		return unreifiedRels == null ? new HashSet<GraphArc>() : unreifiedRels;
	}
		
	private void unreifyRelationInstances() {
		if (artifactToUnreifiedRels != null) {
			return;
		}

		artifactToUnreifiedRels = new HashMap<OWLNamedIndividual, Set<GraphArc>>();

		for(OWLNamedIndividual individual : owlOntology.getIndividualsInSignature()) {
            for (Entry<OWLObjectPropertyExpression, Collection<OWLIndividual>> entry : EntitySearcher
                    .getObjectPropertyValues(individual, owlOntology).asMap()
                    .entrySet()) {
				for(OWLIndividual refIndividual : entry.getValue()) {
					GraphArc arc = createArc(individual, (OWLNamedIndividual)refIndividual, owlModelManager.getRendering(entry.getKey()));
					
					Set<GraphArc> outgoingUnreifiedRels = artifactToUnreifiedRels.get(individual);
					if (outgoingUnreifiedRels == null) {
						outgoingUnreifiedRels = new HashSet<GraphArc>();
						artifactToUnreifiedRels.put(individual, outgoingUnreifiedRels);
					}
					outgoingUnreifiedRels.add(arc);
					
					Set<GraphArc> incomingUnreifiedRels = artifactToUnreifiedRels.get(refIndividual);
					if (incomingUnreifiedRels == null) {
						incomingUnreifiedRels = new HashSet<GraphArc>();
						artifactToUnreifiedRels.put((OWLNamedIndividual)refIndividual, incomingUnreifiedRels);
					}
					incomingUnreifiedRels.add(arc);
				}
			}
		}
	}
	
	private Set<GraphArc> findIncomingIndividualRelationships(OWLEntity entityOfInterest, boolean mustBeVisible) {
		Set<GraphArc> arcs = new HashSet<GraphArc>();

		if (!(entityOfInterest instanceof OWLNamedIndividual)) {
            return arcs;
        }
		if(!filterManager.isArcTypeVisible(DIRECT_INDIVIDUAL_SLOT_TYPE)) {
            return arcs;
        }

		OWLNamedIndividual destIndiv = (OWLNamedIndividual) entityOfInterest;
        for (OWLClassExpression refNode : EntitySearcher.getTypes(destIndiv,
                owlOntology)) {
			if(refNode instanceof OWLClass) {
				OWLClass clsOwner = (OWLClass)refNode;
				if (isDisplayableNode(clsOwner, mustBeVisible)) {
					String relType = DIRECT_INDIVIDUAL_SLOT_TYPE;
	
					arcs.add(createArc(clsOwner, destIndiv, relType));
				}
			}
		}
		
		return arcs;
	}
	
	private Set<GraphArc> findIncomingConditionsRelationships(OWLEntity entityOfInterest, boolean mustBeVisible) {
		Set<GraphArc> arcs = new HashSet<GraphArc>();

		if (!(entityOfInterest instanceof OWLClass)) {
			return arcs;
		}

		OWLClass owlClass = (OWLClass) entityOfInterest;
        Collection<OWLAxiom> axioms = EntitySearcher.getReferencingAxioms(
                owlClass, owlOntology);
		for(OWLAxiom axiom : axioms) {
			if(axiom.getAxiomType().equals(AxiomType.SUBCLASS_OF)) {
				OWLSubClassOfAxiom subClassAxiom = (OWLSubClassOfAxiom)axiom;
				OWLClassExpression subClassExpression = subClassAxiom.getSubClass();
				
				if(subClassExpression instanceof OWLClass) {
					OWLClassExpression superClassExpression = subClassAxiom.getSuperClass();
					if(superClassExpression instanceof OWLQuantifiedRestriction) {
						OWLQuantifiedRestriction restriction = (OWLQuantifiedRestriction)superClassExpression;
						if(restriction.getFiller() instanceof OWLClass) {
							String relType = owlModelManager.getRendering(restriction.getProperty());
							if(restriction instanceof OWLObjectSomeValuesFrom) {
                                relType += SUB_CLASS_SOME_VALUE_OF;
                            } else {
                                relType += SUB_CLASS_ALL_VALUES;
                            }
							
							if(!filterManager.isArcTypeVisible(relType)) {
                                continue;
                            }
							
							OWLEntity source = (OWLClass)subClassExpression;
							OWLEntity target = (OWLClass)restriction.getFiller();
							
							if(isDisplayableNode(source, mustBeVisible) && isDisplayableNode(target, mustBeVisible)) {
								arcs.add(createArc(source, target, relType));
							}
						}
					}
				}
			}
		}
		
		return arcs;
	}
	
	private Set<GraphArc> findOutgoingConditionsRelationships(OWLEntity entityOfInterest, boolean mustBeVisible) {
		Set<GraphArc> arcs = new HashSet<GraphArc>();

		if (!(entityOfInterest instanceof OWLClass)) {
			return arcs;
		}

		OWLClass owlClass = (OWLClass) entityOfInterest;
		
        convertOWLClassExpressionsToArcs(owlClass,
                EntitySearcher.getSuperClasses(owlClass, owlOntology), arcs,
                null, mustBeVisible);
		
		OWLIconProviderImpl iconProvider = new OWLIconProviderImpl(owlModelManager);
		Icon icon = iconProvider.getIcon(owlClass);
        convertOWLClassExpressionsToArcs(owlClass,
                EntitySearcher.getEquivalentClasses(owlClass, owlOntology),
                arcs, icon, mustBeVisible);
		
		return arcs;
	}
	
    private void convertOWLClassExpressionsToArcs(OWLClass owlClass,
            Collection<OWLClassExpression> expressions, Set<GraphArc> arcs,
            Icon icon, boolean mustBeVisible) {
		for(OWLClassExpression expression : expressions) {
			if(expression.getClassExpressionType().equals(ClassExpressionType.OBJECT_SOME_VALUES_FROM) 
					|| expression.getClassExpressionType().equals(ClassExpressionType.OBJECT_ALL_VALUES_FROM)) {
				convertOWLClassExpressionToArcs(owlClass, expression, arcs, icon, mustBeVisible);
			}
			else if(expression.getClassExpressionType().equals(ClassExpressionType.OBJECT_INTERSECTION_OF)) {
				for(OWLClassExpression e : expression.asConjunctSet()) {
					convertOWLClassExpressionToArcs(owlClass, e, arcs, icon, mustBeVisible);
				}
			}
		}
	}
	
	private void convertOWLClassExpressionToArcs(OWLClass owlClass, OWLClassExpression expression, Set<GraphArc> arcs, Icon icon, boolean mustBeVisible) {
		boolean isSubClass = true;
		if(icon != null) {
			isSubClass = false; 
		}
		
		for(OWLClassExpression e : expression.asConjunctSet()) {
			if(e instanceof OWLQuantifiedRestriction) {
				OWLQuantifiedRestriction restriction = (OWLQuantifiedRestriction)e;
				if(restriction.getFiller() instanceof OWLClass) {
					String relType = owlModelManager.getRendering(restriction.getProperty());
					if(isSubClass) {
						if(restriction instanceof OWLObjectSomeValuesFrom) {
                            relType += SUB_CLASS_SOME_VALUE_OF;
                        } else {
                            relType += SUB_CLASS_ALL_VALUES;
                        }
					}
					else {
						if(restriction instanceof OWLObjectSomeValuesFrom) {
                            relType += EQUIVALENT_CLASS_SOME_VALUE_OF;
                        } else {
                            relType += EQUIVALENT_CLASS_ALL_VALUES;
                        }
					}
					
					if(!filterManager.isArcTypeVisible(relType)) {
                        continue;
                    }
					
					if(isDisplayableNode( (OWLClass)restriction.getFiller(), mustBeVisible)) {
						arcs.add(createArc(owlClass, (OWLClass)restriction.getFiller(), relType, icon));
					}
				}
			}
		}
	}
	
	/**
	 * Creates relationships between entityOfInterest and its' direct parents.
	 * 
	 * @return A list of child to parent relationships.
	 */
	protected Set<GraphArc> loadParents(OWLEntity entityOfInterest, boolean mustBeVisible) {
		Set<GraphArc> parents = new HashSet<GraphArc>();
		
		if(!(entityOfInterest instanceof OWLClass)) {
            return parents;
        }
		if(!filterManager.isArcTypeVisible(DIRECT_SUBCLASS_SLOT_TYPE)) {
            return parents;
        }
		
		OWLClass clsOfInterest = (OWLClass)entityOfInterest;
		
		for(OWLClass parentCls : owlEditorKit.getOWLModelManager().getOWLHierarchyManager().getOWLClassHierarchyProvider().getParents(clsOfInterest)) {
			if (isDisplayableNode(parentCls, mustBeVisible)) {
				GraphArc arc = createArc(parentCls, clsOfInterest, DIRECT_SUBCLASS_SLOT_TYPE);
				arc.setInverted(false);
				parents.add(arc);
			}
		}

		return parents;
	}
	
	/**
	 * Creates relationships between entityOfInterest and its' direct children.
	 * 
	 * @return A list of parent to child relationships.
	 */
	protected Set<GraphArc> loadChildren(OWLEntity entityOfInterest, boolean mustBeVisible) {
		Set<GraphArc> children = new HashSet<GraphArc>();
		
		if(!(entityOfInterest instanceof OWLClass)) {
            return children;
        }
		if(!filterManager.isArcTypeVisible(DIRECT_SUBCLASS_SLOT_TYPE)) {
            return children;
        }
		
		OWLClass clsOfInterest = (OWLClass)entityOfInterest;
		
		for(OWLClass childCls : owlEditorKit.getOWLModelManager().getOWLHierarchyManager().getOWLClassHierarchyProvider().getChildren(clsOfInterest)) {
			if (isDisplayableNode(childCls, mustBeVisible)) {
				GraphArc arc = createArc(clsOfInterest, childCls, DIRECT_SUBCLASS_SLOT_TYPE);
				children.add(arc);
			}
		}

		return children;
	}
	
	private Set<GraphArc> findOutgoingIndividualRelationships(OWLEntity entityOfInterest, boolean mustBeVisible) {
		Set<GraphArc> incomingInstanceRels = new HashSet<GraphArc>();

		if (!(entityOfInterest instanceof OWLClass)) {
            return incomingInstanceRels;
        }
		if(!filterManager.isArcTypeVisible(DIRECT_INDIVIDUAL_SLOT_TYPE)) {
            return incomingInstanceRels;
        }

		OWLClass owlClass = (OWLClass) entityOfInterest;
        for (OWLIndividual individual : EntitySearcher.getIndividuals(owlClass,
                owlOntology)) {
			if(individual instanceof OWLNamedIndividual) {
				OWLNamedIndividual namedIndividual = (OWLNamedIndividual)individual;
				if(isDisplayableNode(namedIndividual, mustBeVisible)) {
					String relType = DIRECT_INDIVIDUAL_SLOT_TYPE;
					GraphArc arc = createArc(owlClass, namedIndividual, relType);
					incomingInstanceRels.add(arc);
				}
			}
		}

		return incomingInstanceRels;
	}
	
    private Set<OWLEntity> getOWLClasses(
            Collection<OWLClassExpression> owlExpressions) {
		Set<OWLEntity> domains = new HashSet<OWLEntity>();
		for(OWLClassExpression expression : owlExpressions) {
			if(expression instanceof OWLClass) {
				domains.add((OWLClass)expression);
			}
		}
		
		return domains;
	}
	
	private void createDomainRangeRels(Set<OWLEntity> domains, Set<OWLEntity> ranges, OWLObjectProperty property) {
		// make relationships between all named classes in the domain and all named classes in the range
		for(OWLEntity domainClass : domains) {
			GraphNode srcNode = new DefaultGraphNode(domainClass);
			for (OWLEntity rangeClass : ranges) {
				GraphNode destNode = new DefaultGraphNode(rangeClass);
				String relType = owlModelManager.getRendering(property) + SUFFIX_DOMAIN_RANGE;
				
				GraphArc arc = createArc(srcNode, destNode, relType, null);
				if(!domainRangeRelsBuffer.contains(arc)) {
					domainRangeRelsBuffer.add(arc);
				}
			}
		}
	}
	
	private void createDomainRangeRels() {
		domainRangeRelsBuffer = new HashSet<GraphArc>();
		
		Set<OWLObjectProperty> properties = owlOntology.getObjectPropertiesInSignature();
		
		for(OWLObjectProperty property : properties) {
			for(OWLObjectProperty owlObjectProperty : property.getObjectPropertiesInSignature()) {
                Collection<OWLClassExpression> domainVals = EntitySearcher
                        .getDomains(owlObjectProperty, owlOntology);
                Collection<OWLClassExpression> rangeVals = EntitySearcher
                        .getRanges(owlObjectProperty, owlOntology);
				
				if (domainVals.isEmpty() && !rangeVals.isEmpty()) {
					domainVals.add(owlModelManager.getOWLEntityFinder().getOWLClass("Thing"));
				} else if (rangeVals.isEmpty() && !domainVals.isEmpty()) {
					rangeVals.add(owlModelManager.getOWLEntityFinder().getOWLClass("Thing"));
				}
				
				Set<OWLEntity> domains = getOWLClasses(domainVals);
				Set<OWLEntity> ranges = getOWLClasses(rangeVals);
				
				createDomainRangeRels(domains, ranges, owlObjectProperty);
			}
		}
	}
	
	private Collection<GraphArc> getDomainRangeRelationships() {
		if (domainRangeRelsBuffer == null) {
			createDomainRangeRels();
		}
		return domainRangeRelsBuffer;
	}
	
	/**
	 * Creates a GraphArc with the given parameters without actually adding the GraphNodes or
	 * GraphArc to the model.
	 * 
	 * @return A GraphArc object.
	 */
	protected GraphArc createArc(OWLEntity srcCls, OWLEntity targetCls, String relType) {
		return createArc(srcCls, targetCls, relType, null);
	}
	
	protected GraphArc createArc(OWLEntity srcCls, OWLEntity targetCls, String relType, Icon icon) {
		GraphNode srcNode = new DefaultGraphNode(srcCls);
		GraphNode destNode = new DefaultGraphNode(targetCls);

		return createArc(srcNode, destNode, relType, icon);
	}

	/**
	 * Creates a GraphArc with the given parameters without actually adding the GraphNodes or
	 * GraphArc to the model.
	 * 
	 * @return A GraphArc object.
	 */
	protected GraphArc createArc(GraphNode srcNode, GraphNode destNode, String relType, Icon icon) {
		String key = srcNode.getUserObject().toString() + relType + destNode.getUserObject().toString();

		return new DefaultGraphArc(key, srcNode, destNode, icon, relType);
	}
	
	/**
	 * Performs a search against the knowledge base and displays the neighborhood for the results.
	 * 
	 * @param searchString
	 */
	public Collection<? extends OWLEntity> search(String searchString, FilterManager filterManager) {
		restrictToArcType = "";
		this.filterManager = filterManager;
		
		Set<OWLEntity> searchResults = new HashSet<OWLEntity>();
		Set<? extends OWLEntity> matchingClasses = owlModelManager.getOWLEntityFinder().getMatchingOWLClasses(searchString, true, Pattern.CASE_INSENSITIVE);
		Set<? extends OWLEntity> matchingIndividuals = owlModelManager.getOWLEntityFinder().getMatchingOWLIndividuals(searchString, true, Pattern.CASE_INSENSITIVE);

		searchResults.addAll(matchingClasses);
		searchResults.addAll(matchingIndividuals);
		
		if (searchResults != null) {
			showNeighborhood(searchResults, true);
		}

		return searchResults;
	}
	
	/**
	 * Hides all ascendants of the given node.
	 */
	public void hideAscendants(GraphNode graphNode) {
		Set<IRI> seen = new HashSet<IRI>();

		hideAscendants(graphNode, seen);
	}
	
	/**
	 * Hides all incoming arcs and attempts to hide any nodes and their arcs.
	 * 
	 * @param graphNode
	 * @param seen The list of recursively visited items, need this to avoid cycles
	 */
	private void hideAscendants(GraphNode graphNode, Set<IRI> seen) {
		GraphArc[] arcs = graphNode.getArcs().toArray(new GraphArc[graphNode.getArcs().size()]); 
		seen.add(((OWLEntity) graphNode.getUserObject()).getIRI());

		for (GraphArc arc : arcs) {
			Object userObject = arc.getSource().getUserObject();
			GraphNode node = getNode(userObject);

			// recursively collapse the child node, check our seen object in order to avoid cycles
			if (node != null && !node.equals(graphNode)) {
				removeArc(arc.getUserObject());
				if (!seen.contains(((OWLEntity) userObject).getIRI())) {
					hideAll(node, seen);
				}

				if (isRemovable(node)) {
					removeNode(userObject);
				}
			} else {
				userObject = arc.getDestination().getUserObject();
				seen.add(((OWLEntity) userObject).getIRI());
			}
		}
	}
	
	/**
	 * Hides all arcs recursively from the given graphNode.
	 * 
	 * @param graphNode
	 * @param seen Set of URIs that have already been recursed on
	 */
	private void hideAll(GraphNode graphNode, Set<IRI> seen) {
		GraphArc[] arcs = graphNode.getArcs().toArray(new GraphArc[graphNode.getArcs().size()]); 
		seen.add(((OWLEntity) graphNode.getUserObject()).getIRI());

		for (GraphArc arc : arcs) {
			Object userObject = arc.getDestination().getUserObject();
			GraphNode node = getNode(userObject);
			if (node == null || node.equals(graphNode)) {
				continue;
			}

			// recursively collapse the child node, check our seen object in order to avoid cycles
			removeArc(arc.getUserObject());
			if (!seen.contains(((OWLEntity) userObject).getIRI())) {
				hideAll(node, seen);
			}

			if (node != null && isRemovable(node)) {
				removeNode(userObject);
			}
		}
	}
	
	private Collection<Object> generateArcTypes() {
		Set<Object> types = new HashSet<Object>();
		types.add(ProtegeGraphModel.DIRECT_SUBCLASS_SLOT_TYPE);
		types.add(ProtegeGraphModel.DIRECT_INDIVIDUAL_SLOT_TYPE);
		types.addAll(super.getArcTypes());
		
		return types;
	}
	
	@Override
	public Collection<Object> getArcTypes() {
		if(arcTypes == null) {
			arcTypes = generateArcTypes();
		}
		else {
			Collection<Object> types = generateArcTypes();
			if(types.size() != arcTypes.size()) {
				arcTypes.addAll(types);
			}
		}
		return arcTypes;
	}
	
	/**
	 * Expands the given graphNode by showing it's neighborhood and keeping existing nodes.
	 * 
	 * @param graphNode
	 */
	public void expandNode(GraphNode graphNode) {
		showNeighborhood((OWLEntity) graphNode.getUserObject(), false);
	}
	
	/**
	 * Expands the given graphNode based on a specific arcType and keeping existing nodes.
	 * 
	 * @param graphNode
	 */
	public void expandNode(GraphNode graphNode, String arcType) {
		graphNode.setHighlighted(false);
		
		restrictToArcType = arcType;
		showNeighborhood((OWLEntity) graphNode.getUserObject(), false);
		restrictToArcType = "";
	}

	/**
	 * Collapses the given graphNode.
	 */
	public void collapseNode(GraphNode graphNode) {
		Set<IRI> seen = new HashSet<IRI>();
		
		graphNode.setHighlighted(false);

		collapseNode(graphNode, seen);
	}
	
	/**
	 * Gets all the incoming and outgoing arcs for a given OWLEntity.
	 * 
	 * @return All associated arcs for a given OWLEntity.
	 */
	private Collection<GraphArc> getArcsForEntity(OWLEntity entity) {
		Set<GraphArc> arcs = new HashSet<GraphArc>();

		arcs.addAll(createIncomingRelationships(entity, false));
		arcs.addAll(createOutgoingRelationships(entity, false));

		return arcs;
	}
	
	public void resetNodeToArcCount() {
		for(OWLEntity owlEntity : frameToArcCount.keySet()) {
			frameToArcCount.put(owlEntity, (Set<GraphArc>)getArcsForEntity(owlEntity));
		}
	}
	
	/**
	 * Gets the number of relationships for a given OWLEntity. If the entity doesn't exist in the cache,
	 * then it calculates the count.
	 * 
	 * @return The number of incoming and outgoing relationships for the frame.
	 */
	private Integer getNodeToArcCount(OWLEntity entity) {
		Set<GraphArc> arcs = getCachedArcsForEntity(entity);

		return arcs.size();
	}
	
	public Set<GraphArc> getCachedArcsForEntity(OWLEntity entity) {
		Set<GraphArc> arcs = frameToArcCount.get(entity);
		if (arcs == null) {
			arcs = (Set<GraphArc>)getArcsForEntity(entity);
			frameToArcCount.put(entity, arcs);
		}

		return arcs;
	}
	
	/**
	 * Collapses the given graphNode by recursively removing all outgoing relationships and nodes
	 * that become separate components.
	 * 
	 * @param graphNode
	 * @param seen The list of recursively visited items, need this to avoid cycles
	 */
	private void collapseNode(GraphNode graphNode, Set<IRI> seen) {
		GraphArc[] arcs = graphNode.getArcs().toArray(new GraphArc[graphNode.getArcs().size()]); //createOutgoingRelationships((Frame) graphNode.getUserObject(), true);
		seen.add(((OWLEntity) graphNode.getUserObject()).getIRI());

		for (GraphArc arc : arcs) {
			Object userObject = arc.getDestination().getUserObject();
			GraphNode node = getNode(userObject);

			// recursively collapse the child node, check our seen object in order to avoid cycles
			if (node != null && !node.equals(graphNode)) {
				removeArc(arc.getUserObject());
				if (isRecursableArc(seen, userObject, arc)) {
					collapseNode(node, seen);
				}

				if (isRemovable(node)) {
					removeNode(userObject);
				}
			}
		}
	}
	
	/**
	 * Determines whether the given GraphNode is removable. It is removable if it does not have any
	 * arcs or the only arcs it has are self loops.
	 * 
	 * @param node
	 * @return True if removable.
	 */
	private boolean isRemovable(GraphNode node) {
		for (GraphArc arc : node.getArcs()) {
			if (!arc.getDestination().equals(arc.getSource())) {
				return false;
			}
		}

		return true;
	}
	
	private boolean isRecursableArc(Set<IRI> seen, Object userObject, GraphArc arc) {
		return !seen.contains(((OWLEntity) userObject).getIRI()) && (arc.getType().equals(DIRECT_SUBCLASS_SLOT_TYPE) || arc.getType().equals(DIRECT_INDIVIDUAL_SLOT_TYPE));
	}
	
	/**
	 * Checks if the given graphNode can be expanded, i.e. has relationships.
	 * 
	 */
	public boolean isExpandable(GraphNode graphNode) {
		return getNodeToArcCount((OWLEntity) graphNode.getUserObject()) > 0;
	}
	
	/**
	 * Determines if the given graphNode is expanded.
	 * 
	 * @return True if already expanded.
	 */
	public boolean isExpanded(GraphNode graphNode) {
		int modelArcsSize = graphNode.getArcs().size();
		int frameArcsSize = getNodeToArcCount((OWLEntity) graphNode.getUserObject());

		return modelArcsSize >= frameArcsSize;
	}
}
