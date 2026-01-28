/**
 * Copyright 2025-2026 Naftiko
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package io.naftiko.adapter.proxy;

import java.util.List;
import org.restlet.Server;
import org.restlet.data.Protocol;
import org.restlet.routing.Router;
import org.restlet.routing.TemplateRoute;
import org.restlet.routing.Variable;
import io.naftiko.Adapter;
import io.naftiko.Capability;
import io.naftiko.config.ConsumesConfig;
import io.naftiko.config.ExposesConfig;

/**
 * ProxyAdapter is an implementation of the Adapter abstract class that sets up an HTTP server using
 * the Restlet Framework acting as a configurable pass thru proxy.
 */
public class ProxyAdapter extends Adapter {

    private final Capability capability;
    private final Server server;
    private final Router router;
    private final ExposesConfig exposesConfig;

    public ProxyAdapter(Capability capability, ExposesConfig exposesConfig) {
        this.capability = capability;
        this.exposesConfig = exposesConfig;
        this.server =
                new Server(Protocol.HTTP, exposesConfig.getAddress(), exposesConfig.getPort());
        this.router = new Router();
        boolean expositionSuffixRequired = getConsumesConfig().size() > 1;

        for (ConsumesConfig config : getConsumesConfig()) {
            // Initialize each consume config if needed
            ProxyRestlet proxy = new ProxyRestlet(this, config);
            String pathTemplate = "/{path}";

            if (expositionSuffixRequired && (config.getExpositionSuffix() == null
                    || config.getExpositionSuffix().isEmpty())) {
                throw new IllegalArgumentException(
                        "An expositionSuffix is required when more than one source API is proxied.");
            }

            TemplateRoute route = this.router.attach(pathTemplate, proxy);
            route.getTemplate().getVariables().put("path", new Variable(Variable.TYPE_URI_PATH));
        }

        this.server.setNext(this.router);
    }

    public Capability getCapability() {
        return capability;
    }

    public List<ConsumesConfig> getConsumesConfig() {
        return getCapability().getConfig().getCapability().getConsumes();
    }

    public ExposesConfig getExposesConfig() {
        return exposesConfig;
    }

    public Server getServer() {
        return server;
    }

    public Router getRouter() {
        return router;
    }

    public void init(ExposesConfig config) throws Exception {}

    @Override
    public void start() throws Exception {
        getServer().start();
    }

    @Override
    public void stop() throws Exception {
        getServer().stop();
    }

}
