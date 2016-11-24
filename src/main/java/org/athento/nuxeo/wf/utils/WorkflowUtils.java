package org.athento.nuxeo.wf.utils;

import org.nuxeo.ecm.automation.AutomationService;
import org.nuxeo.ecm.automation.OperationContext;
import org.nuxeo.ecm.automation.OperationException;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.PathRef;
import org.nuxeo.runtime.api.Framework;

import java.util.Map;

/**
 * Created by victorsanchez on 24/11/16.
 */
public final class WorkflowUtils {

    public static final String CONFIG_PATH = "/ExtendedConfig";


    /**
     * Read config value.
     *
     * @param session
     * @param key
     * @return
     */
    public static <T> T readConfigValue(CoreSession session, String key, Class<T> clazz) {
        DocumentModel conf = session.getDocument(new PathRef(
                WorkflowUtils.CONFIG_PATH));
        return clazz.cast(conf.getPropertyValue(key));
    }

    /**
     * Run an operation.
     *
     * @param operationId
     * @param input
     * @param params
     * @param session
     * @return
     * @throws Exception
     */
    public static Object runOperation(String operationId, Object input,
                                      Map<String, Object> params, CoreSession session)
            throws Exception {
        AutomationService automationManager = Framework
                .getLocalService(AutomationService.class);
        // Input setting
        OperationContext ctx = new OperationContext(session);
        ctx.setInput(input);
        return automationManager.run(ctx, operationId, params);
    }


}
