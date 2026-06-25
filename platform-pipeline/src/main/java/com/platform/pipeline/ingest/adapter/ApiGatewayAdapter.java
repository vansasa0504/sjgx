package com.platform.pipeline.ingest.adapter;

import com.platform.pipeline.ingest.HttpAdapter;

public class ApiGatewayAdapter extends HttpAdapter {
    @Override
    public String protocol() { return "API_GW"; }
}
