package lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.ScheduledEvent;

public class RefreshRequestHandler implements RequestHandler<ScheduledEvent, Void> {
    @Override
    public Void handleRequest(ScheduledEvent event, Context context) {
        // Your logic here. For example, process the event and perform some actions.
        context.getLogger().log("Event: " + event);

        // No need to return anything for an EventBridge triggered Lambda
        return null;
    }
}
