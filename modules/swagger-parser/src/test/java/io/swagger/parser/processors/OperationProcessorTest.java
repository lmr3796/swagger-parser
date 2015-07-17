package io.swagger.parser.processors;


import io.swagger.models.Operation;
import io.swagger.models.RefResponse;
import io.swagger.models.ResponseImpl;
import io.swagger.models.Swagger;
import io.swagger.models.parameters.Parameter;
import io.swagger.models.refs.RefFormat;
import io.swagger.parser.ResolverCache;
import mockit.Expectations;
import mockit.FullVerifications;
import mockit.Injectable;
import mockit.Mocked;
import org.testng.annotations.Test;

import java.util.List;

import static org.testng.Assert.assertEquals;

public class OperationProcessorTest {

    @Injectable
    ResolverCache cache;

    @Injectable
    Swagger swagger;

    @Mocked
    ParameterProcessor parameterProcessor;

    @Mocked
    ResponseProcessor responseProcessor;

    @Test
    public void testProcessOperation(@Injectable final List<Parameter> inputParameterList,
                                     @Injectable final List<Parameter> outputParameterList,
                                     @Injectable final ResponseImpl incomingResponseImpl,
                                     @Injectable final ResponseImpl resolvedResponseImpl) throws Exception {

        Operation operation = new Operation();
        operation.setParameters(inputParameterList);

        final String ref = "http://my.company.com/path/to/file.json#/foo/bar";
        RefResponse refResponse = new RefResponse(ref);

        operation.response(200, refResponse);
        operation.response(400, incomingResponseImpl);

        new Expectations() {{
            new ParameterProcessor(cache, swagger);
            times = 1;
            result = parameterProcessor;
            new ResponseProcessor(cache, swagger);
            times = 1;
            result = responseProcessor;

            parameterProcessor.processParameters(inputParameterList);
            times = 1;
            result = outputParameterList;

            cache.loadRef(ref, RefFormat.URL, ResponseImpl.class);
            times = 1;
            result = resolvedResponseImpl;

            responseProcessor.processResponse(incomingResponseImpl);
            times = 1;
            responseProcessor.processResponse(resolvedResponseImpl);
            times = 1;
        }};

        new OperationProcessor(cache, swagger).processOperation(operation);

        new FullVerifications() {{}};

        assertEquals(operation.getResponses().get("200"), resolvedResponseImpl);
        assertEquals(operation.getParameters(), outputParameterList);
    }
}
