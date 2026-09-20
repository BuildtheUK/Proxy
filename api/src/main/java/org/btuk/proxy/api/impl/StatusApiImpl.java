package org.btuk.proxy.api.impl;

import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;
import org.btuk.proxy.api.StatusApi;
import org.btuk.proxy.api.model.Status;

@Path("/status")
public class StatusApiImpl implements StatusApi {

    @Override
    public Response getStatus() {
        Status status = new Status();
        status.setStatus("UP");
        return Response.ok(status).build();
    }
}
