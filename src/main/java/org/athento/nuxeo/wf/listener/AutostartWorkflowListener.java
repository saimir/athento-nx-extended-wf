package org.athento.nuxeo.wf.listener;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.athento.nuxeo.wf.utils.WorkflowExtConstants;
import org.athento.nuxeo.wf.utils.WorkflowUtils;
import org.nuxeo.ecm.core.api.*;
import org.nuxeo.ecm.core.event.Event;
import org.nuxeo.ecm.core.event.EventListener;
import org.nuxeo.ecm.core.event.impl.DocumentEventContext;
import org.nuxeo.ecm.platform.actions.ActionContext;
import org.nuxeo.ecm.platform.actions.ejb.ActionManager;
import org.nuxeo.ecm.platform.actions.jsf.JSFActionContext;
import org.nuxeo.ecm.platform.actions.seam.SeamActionContext;
import org.nuxeo.ecm.platform.routing.api.DocumentRoute;
import org.nuxeo.ecm.platform.routing.api.DocumentRoutingService;
import org.nuxeo.ecm.platform.routing.core.impl.GraphRoute;
import org.nuxeo.ecm.platform.ui.web.util.SeamContextHelper;
import org.nuxeo.runtime.api.Framework;

import javax.faces.context.FacesContext;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Listener to manage start workflow after document is created.
 */
public class AutostartWorkflowListener implements EventListener {

    /**
     * Log.
     */
    private static final Log LOG = LogFactory
            .getLog(AutostartWorkflowListener.class);


    /**
     * Handle auto-start.
     *
     * @param event
     * @throws Exception
     */
    @Override
    public void handleEvent(Event event) throws ClientException {
        if (event != null) {
            if (event.getContext() instanceof DocumentEventContext) {
                DocumentModel document = ((DocumentEventContext) event.getContext()).getSourceDocument();
                // Start workflow (without Seam events threw)
                DocumentModel selectedRoute = getFirstAutostartRouteModelForDocument(document, event.getContext().getCoreSession());
                if (selectedRoute != null) {
                    DocumentRoute currentRoute = selectedRoute.getAdapter(DocumentRoute.class);
                    if (document.hasFacet("autoinitiable")) {
                        List<String> documentIds = new ArrayList<String>();
                        documentIds.add(document.getId());
                        currentRoute.setAttachedDocuments(documentIds);
                        getDocumentRoutingService().createNewInstance(
                                currentRoute.getDocument().getName(), currentRoute.getAttachedDocuments(),
                                event.getContext().getCoreSession(), true);
                        // Add audit with autostart
                        String comment = "Workflow " + currentRoute.getName() + " was autocreated";
                        WorkflowUtils.newEntry(document, event.getContext().getPrincipal().getName(),
                                WorkflowExtConstants.WORKFLOW_AUTOSTART_EVENT, WorkflowExtConstants.WORKFLOW_CATEGORY, comment, null, null);
                    }
                }
            }
        }
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

    /**
     * Get first autostart route model.
     *
     * @return
     * @throws ClientException
     */
    public DocumentModel getFirstAutostartRouteModelForDocument(DocumentModel document, CoreSession session) throws ClientException {
        DocumentRoutingService documentRoutingService = Framework.getLocalService(DocumentRoutingService.class);
        List<DocumentModel> routeModels = documentRoutingService.searchRouteModels(
                session, "");
        for (Iterator<DocumentModel> it = routeModels.iterator(); it.hasNext(); ) {
            DocumentModel route = it.next();
            Object graphRouteObj = route.getAdapter(GraphRoute.class);
            if (graphRouteObj instanceof GraphRoute) {
                String filter = ((GraphRoute) graphRouteObj).getAvailabilityFilter();
                if (!StringUtils.isBlank(filter)) {
                    if (checkActionFilter(document, session, filter)) {
                        // Check if route has an autostart task
                        if (routeHasAutostartTask((GraphRoute) graphRouteObj)) {
                            return route;
                        }
                    }
                }
            }
        }
        return null;
    }

    /**
     * Check if route has a autostart task.
     *
     * @param graphRoute
     * @return
     */
    private boolean routeHasAutostartTask(GraphRoute graphRoute) {
        return graphRoute.getStartNode() != null &&
                (Boolean) graphRoute.getStartNode().getDocument().getPropertyValue("rnode:executeOnlyFirstTransition");
    }

    /**
     * Check action filter.
     *
     * @param document
     * @param session
     * @param filterId
     * @return
     */
    private boolean checkActionFilter(DocumentModel document, CoreSession session, String filterId) {
        ActionManager actionService = Framework.getService(ActionManager.class);
        return actionService.checkFilter(filterId, createActionContext(document, session));
    }

    /**
     * Create action context.
     *
     * @param document
     * @param session
     * @return
     */
    private ActionContext createActionContext(DocumentModel document, CoreSession session) {
        ActionContext ctx;
        FacesContext faces = FacesContext.getCurrentInstance();
        if (faces == null) {
            ctx = new SeamActionContext();
        } else {
            ctx = new JSFActionContext(faces);
        }
        ctx.setCurrentDocument(document);
        ctx.setDocumentManager(session);
        ctx.setCurrentPrincipal((NuxeoPrincipal) session.getPrincipal());
        ctx.putLocalVariable("SeamContext", new SeamContextHelper());
        return ctx;
    }
}
