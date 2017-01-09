package org.athento.nuxeo.wf.listener;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.athento.nuxeo.wf.utils.WorkflowUtils;
import org.hibernate.jdbc.Work;
import org.nuxeo.ecm.core.api.*;
import org.nuxeo.ecm.core.event.Event;
import org.nuxeo.ecm.core.event.EventListener;
import org.nuxeo.ecm.core.event.impl.DocumentEventContext;
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

    /**
     * Handle notification.
     *
     * @param event
     * @throws Exception
     */
    @Override
    public void handleEvent(Event event) throws ClientException {
        if (event != null) {
            if (event.getContext() instanceof DocumentEventContext) {
                CoreSession session = event.getContext().getCoreSession();
                // Add task property from event document
                DocumentModel document = ((DocumentEventContext) event.getContext()).getSourceDocument();
                if (document != null) {
                    Task task = WorkflowUtils.getTaskFromDocument(event.getContext());
                    if (task != null) {
                        DocumentModel taskNodeDoc = WorkflowUtils.getDocumentNodeFromTask(session, task);
                        // Check transitions
                        if (taskNodeDoc != null) {
                            LOG.info("Task node doc to check transitions " + taskNodeDoc.getId());
                            String nodeId = (String) taskNodeDoc.getPropertyValue("rnode:nodeId");
                            if (taskNodeDoc.hasSchema("var-" + nodeId)) {
                                try {
                                    String transitionsData = (String) taskNodeDoc.getPropertyValue("var-" + nodeId + ":lfTransitions");
                                    LOG.info("Transitions " + transitionsData);
                                    if (transitionsData != null) {
                                        DocumentModel targetDoc = session.getDocument(new IdRef(task.getTargetDocumentId()));
                                        DocumentModel nextTaskDocument = WorkflowUtils.getTaskDocument(session, targetDoc, "opened");
                                        if (nextTaskDocument != null) {
                                            Task nextTask = nextTaskDocument.getAdapter(Task.class);
                                            DocumentModel nextNodeDocument = WorkflowUtils.getDocumentNodeFromTask(session, nextTask);
                                            if (nextNodeDocument != null) {
                                                String transitionExecuted = getLastTransitionExecuted(taskNodeDoc, (String) nextNodeDocument.getPropertyValue("rnode:nodeId"));
                                                LOG.info("transitionExecuted" + transitionExecuted);
                                                String[] transitions = transitionsData.split(";");
                                                for (String transitionInfo : transitions) {
                                                    String [] transitionData = transitionInfo.split(":");
                                                    if (transitionData.length == 2) {
                                                        String transitionName = transitionData[0];
                                                        if (transitionExecuted.equals(transitionName)) {
                                                            String transition = transitionData[1];
                                                            if (transition != null && !"null".equals(transition)) {
                                                                DocumentModel targetDocument = WorkflowUtils.getTargetDocumentFromTask(session, nextTask);
                                                                session.followTransition(targetDocument, transition);
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                } catch (PropertyException e) {
                                    LOG.info("Property lfTransitions is not found for taskNode " + taskNodeDoc.getId());
                                }
                            }
                        }
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
    private String getLastTransitionExecuted(DocumentModel lastNode, String currentNodeId) {
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
