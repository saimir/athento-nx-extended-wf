package org.athento.nuxeo.wf.operation;


import org.nuxeo.ecm.automation.OperationContext;
import org.nuxeo.ecm.automation.core.Constants;
import org.nuxeo.ecm.automation.core.annotations.Context;
import org.nuxeo.ecm.automation.core.annotations.Operation;
import org.nuxeo.ecm.automation.core.annotations.OperationMethod;
import org.nuxeo.ecm.automation.core.annotations.Param;
import org.nuxeo.ecm.core.api.ClientException;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.platform.routing.api.DocumentRoutingConstants;
import org.nuxeo.ecm.platform.routing.api.DocumentRoutingService;

import java.util.HashMap;
import java.util.Map;

@Operation(id = ResumeNodeOperation.ID, category = "Athento", label = "Resume workflow extended", requires = Constants.WORKFLOW_CONTEXT, description = "Resumes a route instance with force option.")
public class ResumeNodeOperation {
    public static final String ID = "Workflow.ResumeNodeOperation";

    @Context
    protected CoreSession session;

    @Context
    protected OperationContext ctx;

    @Param(name = "workflowInstanceId", required = false)
    protected String workflowInstanceId;

    @Param(name = "nodeId", required = false)
    protected String nodeId;

    @Param(name = "force", required = false)
    protected boolean forceResume = false;

    @Context
    protected DocumentRoutingService documentRoutingService;

    @OperationMethod
    public void resumeWorkflow() throws ClientException {
        if (workflowInstanceId == null) {
            workflowInstanceId = (String) ctx.get("workflowInstanceId");
        }
        if (nodeId == null) {
            nodeId = (String) ctx.get("nodeId");
        }
        if (workflowInstanceId == null) {
            throw new ClientException(
                    "Can not resume workflow instance with id "
                            + workflowInstanceId
                            + ". No current instance in the context");
        }

        Map<String, Object> params = new HashMap<>();
        params.put(DocumentRoutingConstants.WORKFLOW_FORCE_RESUME, forceResume);

        documentRoutingService.resumeInstance(workflowInstanceId, nodeId, params,
                null, session);
    }
}