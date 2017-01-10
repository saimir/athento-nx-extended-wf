package org.athento.nuxeo.wf.listener;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.athento.nuxeo.wf.utils.WorkflowUtils;
import org.hibernate.jdbc.Work;
import org.nuxeo.ecm.core.api.*;
import org.nuxeo.ecm.core.event.Event;
import org.nuxeo.ecm.core.event.EventListener;
import org.nuxeo.ecm.core.event.impl.DocumentEventContext;
import org.nuxeo.ecm.platform.relations.api.Graph;
import org.nuxeo.ecm.platform.routing.api.DocumentRoutingConstants;
import org.nuxeo.ecm.platform.routing.core.impl.GraphNode;
import org.nuxeo.ecm.platform.routing.core.impl.GraphRoute;
import org.nuxeo.ecm.platform.routing.core.impl.GraphRouteImpl;
import org.nuxeo.ecm.platform.task.Task;
import org.nuxeo.runtime.api.Framework;

import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Listener for task completed listener.
 */
public class TaskCompletedListener implements EventListener {

    private static final Log LOG = LogFactory
            .getLog(TaskCompletedListener.class);

    /** Last transition execution control. */
    private static String lastExecutedTransition = null;

    /**
     * Handle notification.
     *
     * @param event
     * @throws Exception
     */
    @Override
    public void handleEvent(Event event) {
        if (event != null) {
            CoreSession session = event.getContext().getCoreSession();
            if (event.getName().equals(DocumentRoutingConstants.Events.beforeRouteStart.name())) {
                // Start task
                GraphRouteImpl startRoute = (GraphRouteImpl) event.getContext().getProperty("documentElementEventContextKey");
                GraphNode startNode = startRoute.getStartNode();
                List<GraphNode.Transition> nodeTransitions = startNode.getOutputTransitions();
                if (nodeTransitions.size() > 0) {
                    String transitionExecuted = nodeTransitions.get(0).getId();
                    if (!startRoute.getAttachedDocumentModels().isEmpty()) {
                        executeTransition(session, startRoute.getAttachedDocumentModels().get(0), startNode, transitionExecuted);
                    }
                }
            } else if (event.getName().equals("documentModified")) {
                // Check transition after node document modification
                DocumentModel autoNode = ((DocumentEventContext) event.getContext()).getSourceDocument();
                if (autoNode.getType().equals("RouteNode")) {
                    GraphNode node = autoNode.getAdapter(GraphNode.class);
                    if (!node.hasTask()) {
                        DocumentModel startRouteDoc = session.getDocument(autoNode.getParentRef());
                        if (startRouteDoc != null) {
                            GraphRoute startRoute = startRouteDoc.getAdapter(GraphRoute.class);
                            // Check transitions
                            List<GraphNode.Transition> nodeTransitions = node.getOutputTransitions();
                            if (nodeTransitions.size() > 0) {
                                String transitionExecuted = nodeTransitions.get(0).getId();
                                if (!startRoute.getAttachedDocumentModels().isEmpty()) {
                                    executeTransition(session, startRoute.getAttachedDocumentModels().get(0), node, transitionExecuted);
                                }
                            }
                        }
                    }
                }
            } else {
                // Add task property from event document
                DocumentModel document = ((DocumentEventContext) event.getContext()).getSourceDocument();
                if (document != null) {
                    Task task = WorkflowUtils.getTaskFromDocument(event.getContext());
                    if (task != null) {
                        DocumentModel taskNodeDoc = WorkflowUtils.getDocumentNodeFromTask(session, task);
                        // Check transitions
                        if (taskNodeDoc != null) {
                            LOG.info("Task node doc to check transitions " + taskNodeDoc.getId() + ", " + taskNodeDoc.getName());
                            manageTransitions(session, task, taskNodeDoc);
                        }
                    }
                }
            }
        }
    }

    /**
     * Manage lifecycle transitions.
     *
     * @param session
     * @param task
     * @param taskNodeDoc
     */
    private void manageTransitions(CoreSession session, Task task, DocumentModel taskNodeDoc) {
        String nodeId = (String) taskNodeDoc.getPropertyValue("rnode:nodeId");
        if (taskNodeDoc.hasSchema("var-" + nodeId)) {
            try {
                String transitionsData = (String) taskNodeDoc.getPropertyValue("var-" + nodeId + ":lfTransitions");
                if (transitionsData != null) {
                    DocumentModel targetDoc = session.getDocument(new IdRef(task.getTargetDocumentId()));
                    DocumentModel nextTaskDocument = WorkflowUtils.getTaskDocument(session, targetDoc, "opened");
                    if (nextTaskDocument != null) {
                        Task nextTask = nextTaskDocument.getAdapter(Task.class);
                        DocumentModel nextNodeDocument = WorkflowUtils.getDocumentNodeFromTask(session, nextTask);
                        if (nextNodeDocument != null) {
                            String transitionExecuted = getLastWorkflowTransitionExecuted(taskNodeDoc, (String) nextNodeDocument.getPropertyValue("rnode:nodeId"));
                            GraphNode node = taskNodeDoc.getAdapter(GraphNode.class);
                            executeTransition(session, WorkflowUtils.getTargetDocumentFromTask(session, nextTask), node, transitionExecuted);
                        }
                    }
                }
            } catch (PropertyException e) {
                LOG.info("Property lfTransitions is not found for taskNode " + taskNodeDoc.getId());
            }
        }
    }

    /**
     * Execute transition.
     *
     * @param session
     * @param targetDocument
     * @param node
     * @param transitionExecuted
     */
    private void executeTransition(CoreSession session, DocumentModel targetDocument, GraphNode node, String transitionExecuted) {
        String transitionsData = (String) node.getDocument().getPropertyValue("var-" + node.getId() + ":lfTransitions");
        String[] transitions = transitionsData.split(";");
        for (String transitionInfo : transitions) {
            String[] transitionData = transitionInfo.split(":");
            if (transitionData.length == 2) {
                String transitionName = transitionData[0];
                if (transitionExecuted.equals(transitionName)) {
                    String transition = transitionData[1];
                    if (transition != null && !"null".equals(transition)
                            && !transition.equals(lastExecutedTransition)) {
                        session.followTransition(targetDocument, transition);
                        // Update lastExecutedTransition
                        lastExecutedTransition = transition;
                    }
                }
            }
        }
    }

    /**
     * Get last transition executed.
     *
     * @param lastNode
     * @param currentNodeId
     * @return
     */
    private String getLastWorkflowTransitionExecuted(DocumentModel lastNode, String currentNodeId) {
        if (lastNode == null) {
            return null;
        }
        List<Map<String, Object>> transitions = (List<Map<String, Object>>) lastNode.getPropertyValue("rnode:transitions");
        for (Map<String, Object> transition : transitions) {
            String targetId = (String) transition.get("targetId");
            if (targetId != null && targetId.equals(currentNodeId)) {
                return (String) transition.get("name");
            }
        }
        return null;

    }


}
