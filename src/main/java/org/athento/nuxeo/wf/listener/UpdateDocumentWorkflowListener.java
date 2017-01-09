package org.athento.nuxeo.wf.listener;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.athento.nuxeo.wf.utils.WorkflowExtConstants;
import org.athento.nuxeo.wf.utils.WorkflowUtils;
import org.jboss.seam.contexts.Contexts;
import org.jboss.seam.core.Conversation;
import org.jboss.seam.core.Events;
import org.nuxeo.ecm.core.api.*;
import org.nuxeo.ecm.core.event.Event;
import org.nuxeo.ecm.core.event.EventListener;
import org.nuxeo.ecm.core.event.impl.DocumentEventContext;
import org.nuxeo.ecm.platform.actions.ActionContext;
import org.nuxeo.ecm.platform.actions.ejb.ActionManager;
import org.nuxeo.ecm.platform.actions.jsf.JSFActionContext;
import org.nuxeo.ecm.platform.actions.seam.SeamActionContext;
import org.nuxeo.ecm.platform.audit.impl.ExtendedInfoImpl;
import org.nuxeo.ecm.platform.contentview.jsf.ContentView;
import org.nuxeo.ecm.platform.contentview.seam.ContentViewActions;
import org.nuxeo.ecm.platform.routing.api.DocumentRoute;
import org.nuxeo.ecm.platform.routing.api.DocumentRoutingService;
import org.nuxeo.ecm.platform.routing.core.impl.GraphRoute;
import org.nuxeo.ecm.platform.task.Task;
import org.nuxeo.ecm.platform.task.TaskEventNames;
import org.nuxeo.ecm.platform.ui.web.api.NavigationContext;
import org.nuxeo.ecm.platform.ui.web.util.SeamContextHelper;
import org.nuxeo.ecm.webapp.helpers.EventNames;
import org.nuxeo.runtime.api.Framework;

import javax.faces.context.FacesContext;
import java.io.Serializable;
import java.util.*;

/**
 * Listener to manage workflow after document is modified.
 */
public class UpdateDocumentWorkflowListener implements EventListener {

    /**
     * Log.
     */
    private static final Log LOG = LogFactory
            .getLog(UpdateDocumentWorkflowListener.class);


    /**
     * Handle update document.
     *
     * @param event
     * @throws Exception
     */
    @Override
    public void handleEvent(final Event event) {
        if (event != null) {
            if (event.getContext() instanceof DocumentEventContext) {
                CoreSession session = event.getContext().getCoreSession();
                final DocumentModel document = ((DocumentEventContext) event.getContext()).getSourceDocument();
                DocumentModel selectedRoute = WorkflowUtils.getRouteModelForDocument(document, session);
                if (selectedRoute != null) {
                    LOG.debug("Selected route " + selectedRoute);
                    // Get current task document
                    final DocumentModel taskDocument = WorkflowUtils.getTaskDocument(session, document, "opened");
                    if (taskDocument == null) {
                        return;
                    }
                    LOG.debug("Task document " + taskDocument.getId());
                    final Task task = taskDocument.getAdapter(Task.class);
                    if (task.isOpened()) {
                        // Get node for the task
                        final DocumentModel routeNode = WorkflowUtils.getDocumentNodeFromTask(session, task);
                        if (routeNode != null && routeNode.getCurrentLifeCycleState().equals("suspended")) {
                            LOG.debug("Route node " + routeNode.getId());
                            // Check waiting for task
                            if (isWaitingForNode(routeNode, task)) {
                                LOG.info("Trying with route node " + routeNode.getId());
                                final Map<String, Object> params = new HashMap<>();
                                Map<String, Serializable> taskInfo = WorkflowUtils.getTaskInfo(session, task, true);
                                params.put("WorkflowVariables", taskInfo);
                                params.put("NodeVariables", taskInfo);
                                new UnrestrictedSessionRunner(session) {
                                    @Override
                                    public void run() {
                                        // End task
                                        getDocumentRoutingService()
                                                .endTask(session, task, params, "end");
                                        DocumentModel nextTaskDocument = WorkflowUtils.getTaskDocument(session, document, "opened");
                                        if (nextTaskDocument != null) {
                                            Task nextTask = nextTaskDocument.getAdapter(Task.class);
                                            DocumentModel nextRouteNode = WorkflowUtils.getDocumentNodeFromTask(session, nextTask);
                                            if (nextRouteNode != null && !nextRouteNode.getId().equals(routeNode.getId())) {
                                                // Add audit
                                                addTaskAudit(task, document, event.getContext().getPrincipal().getName());
                                                // Refresh seam events
                                                refreshSeam();
                                            }
                                        } else {
                                            // Add audit
                                            addTaskAudit(task, document, event.getContext().getPrincipal().getName());
                                            // Refresh seam events
                                            refreshSeam();
                                        }
                                    }
                                }.runUnrestricted();
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Add task audit.
     *
     * @param task
     * @param doc
     * @param user
     */
    private void addTaskAudit(Task task, DocumentModel doc, String user) {
        // Add audit
        String comment = "Waiting task " + task.getName() + " completed";
        ExtendedInfoImpl.StringInfo directive = (ExtendedInfoImpl.StringInfo) ExtendedInfoImpl.createExtendedInfo(task.getDirective() != null && !task.getDirective().isEmpty() ? task.getDirective() : task.getType());
        ExtendedInfoImpl.DateInfo dueDate = (ExtendedInfoImpl.DateInfo) ExtendedInfoImpl.createExtendedInfo(task.getDueDate());
        WorkflowUtils.newEntry(doc, user,
                WorkflowExtConstants.WORKFLOW_WAITING_EVENT, WorkflowExtConstants.WORKFLOW_CATEGORY, comment, directive, dueDate);
    }

    /**
     * Refresh seam context.
     */
    private void refreshSeam() {
        if (Contexts.isSessionContextActive()) {
            ContentViewActions contentviewActions = (ContentViewActions) Contexts.getConversationContext().get(
                    "contentViewActions");
            contentviewActions.resetAllContent();
            NavigationContext navigationContext = (NavigationContext) Contexts.getConversationContext().get(
                    "navigationContext");
            try {
                navigationContext.invalidateCurrentDocument();
            } catch (ClientException e) {
                LOG.error("Error invalidating current document "
                        + "on navigation context", e);
            }
            DocumentModel dm = navigationContext.getCurrentDocument();
            if (dm != null) {
                Events.instance().raiseEvent(EventNames.DOCUMENT_CHANGED, dm);
                Events.instance().raiseEvent(
                        EventNames.DOCUMENT_CHILDREN_CHANGED, dm);
            }
            Events.instance().raiseEvent(
                    TaskEventNames.WORKFLOW_TASK_COMPLETED, dm);
        } else {
            LOG.debug("Seam context has not been initialized");
        }
    }

    /**
     * Check if document node is waiting for.
     *
     * @param routeNodeDocument
     * @return
     */
    private boolean isWaitingForNode(DocumentModel routeNodeDocument, Task task) {
        return routeNodeDocument.hasFacet("facet_ro-waiting");
    }

    /**
     * Get routing service.
     *
     * @return
     */
    public DocumentRoutingService getDocumentRoutingService() {
        try {
            return Framework.getService(DocumentRoutingService.class);
        } catch (Exception e) {
            throw new ClientRuntimeException(e);
        }
    }

}
