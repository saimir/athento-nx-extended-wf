<html>
  <body>
    <div style="margin:0; padding:0; background-color:#e9ecef;font-family:Arial,sans-serif;" marginheight="0" marginwidth="0">
      <center>
        <table cellspacing="0" cellpadding="0" border="0" align="center" width="100%" height="100%" style="background-color:#e9ecef;border-collapse:collapse; font-family:Arial,sans-serif;margin:0; padding:0; min-height:100% ! important; width:100% ! important;border:none;">
          <tbody>
            <tr>
              <td align="center" valign="top" style="border-collapse:collapse;margin:0;padding:20px;border-top:0;min-height:100%!important;width:100%!important">
                <table cellspacing="0" cellpadding="0" border="0" style="border-collapse:collapse;border:none;width:100%">
                  <tbody>
                    <tr>
                      <td style="background-color:#f7f7f7;border-bottom:1px dashed #e9ecef;padding:8px 20px;">
                        <p style="font-weight:bold;font-size:15px;margin:0;color:#000;">
                        ${Runtime.getProperty('org.nuxeo.ecm.product.name')}</p>
                      </td>
                    </tr>
                    <tr>
                      <td style="background-color:#fff;padding:8px 20px;"><br/>
                        <p style="margin:0;font-size:14px;">
                        En el documento <a style="color:#22aee8;text-decoration:underline;word-wrap:break-word !important;" href="${docUrl}">${docTitle}</a> tiene <strong>una tarea asignada</strong> a usted o a un grupo al que pertenece.
                        </p><br/>
                        <p style="margin:0;font-size:13px;">
                          <a style="color:#22aee8;text-decoration:underline;word-wrap:break-word !important;" href="${docUrl}">&#187; Ver la tarea en ${docTitle}</a>
                        </p><br/>
                        <#if previewUrl?has_content>
                        <p style="margin:0;font-size:13px;">
                        Si lo desea, puede acceder a la previsualización del documento <a style="color:#22aee8;text-decoration:underline;word-wrap:break-word !important;" href="${host}${previewUrl}">aquí</a>
                        </p><br/>
                        </#if>
                        <p style="margin:0;font-size:13px;">
                            <a style="color:#22aee8;text-decoration:underline;word-wrap:break-word !important; margin-right:20px;" href="${host}/api/athento/v1/workflow/tasks/${taskId}/transition/validate?token=${token}">&#187; Validar</a>
                            <a style="color:#22aee8;text-decoration:underline;word-wrap:break-word !important;" href="${host}/api/athento/v1/workflow/tasks/${taskId}/transition/reject?token=${token}">&#187; Rechazar</a>
                        </p><br/>
                     </td>
                    </tr>
                  </tbody>
                </table>
              </td>
            </tr>
          </tbody>
        </table>
      </center>
    </div>
  </body>
<html>
