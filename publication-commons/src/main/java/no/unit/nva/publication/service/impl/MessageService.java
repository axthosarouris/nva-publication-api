package no.unit.nva.publication.service.impl;

import static java.util.Objects.isNull;
import static no.unit.nva.publication.PublicationServiceConfig.DEFAULT_DYNAMODB_CLIENT;
import static no.unit.nva.publication.model.storage.DynamoEntry.parseAttributeValuesMap;
import static no.unit.nva.publication.storage.model.DatabaseConstants.RESOURCES_TABLE_NAME;
import static nva.commons.core.attempt.Try.attempt;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.GetItemRequest;
import com.amazonaws.services.dynamodbv2.model.GetItemResult;
import com.amazonaws.services.dynamodbv2.model.TransactWriteItem;
import com.amazonaws.services.dynamodbv2.model.TransactWriteItemsRequest;
import java.time.Clock;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import no.unit.nva.identifiers.SortableIdentifier;
import no.unit.nva.model.Publication;
import no.unit.nva.publication.exception.InvalidInputException;
import no.unit.nva.publication.model.business.Message;
import no.unit.nva.publication.model.business.MessageType;
import no.unit.nva.publication.model.business.TicketEntry;
import no.unit.nva.publication.model.business.UserInstance;
import no.unit.nva.publication.model.storage.Dao;
import no.unit.nva.publication.model.storage.IdentifierEntry;
import no.unit.nva.publication.model.storage.MessageDao;
import nva.commons.apigateway.exceptions.NotFoundException;
import nva.commons.core.JacocoGenerated;
import nva.commons.core.StringUtils;

public class MessageService extends ServiceWithTransactions {
    
    public static final String EMPTY_MESSAGE_ERROR = "Message cannot be empty";
    
    public static final String MESSAGE_NOT_FOUND_ERROR = "Could not find message with identifier:";
    
    private final AmazonDynamoDB client;
    private final String tableName;
    private final Clock clockForTimestamps;
    
    private final Supplier<SortableIdentifier> identifierSupplier;
    private final TicketService ticketService;
    
    public MessageService(AmazonDynamoDB client, Clock clockForTimestamps) {
        this(client, clockForTimestamps, defaultIdentifierSupplier());
    }
    
    public MessageService(AmazonDynamoDB client,
                          Clock clockForTimestamps,
                          Supplier<SortableIdentifier> identifierSupplier) {
        super();
        this.client = client;
        this.ticketService = new TicketService(client);
        tableName = RESOURCES_TABLE_NAME;
        this.clockForTimestamps = clockForTimestamps;
        this.identifierSupplier = identifierSupplier;
    }
    
    @JacocoGenerated
    public static MessageService defaultService() {
        return new MessageService(DEFAULT_DYNAMODB_CLIENT, Clock.systemDefaultZone());
    }
    
    public SortableIdentifier createMessage(UserInstance sender,
                                            Publication publication,
                                            String messageText,
                                            MessageType messageType) {
        requireMessageIsNotBlank(messageText);
        
        Message message = createMessageEntry(sender, publication, messageText, messageType);
        return writeMessageToDb(message);
    }
    
    public Message createMessage(TicketEntry ticketEntry, UserInstance sender, String messageText) {
        var newMessage = Message.create(ticketEntry, sender, messageText);
        var dao = newMessage.toDao();
        var transactionRequest = dao.createInsertionTransactionRequest();
        client.transactWriteItems(transactionRequest);
        
        markTicketReadForSenderAndUnreadForRecipient(ticketEntry, sender);
        return fetchEventualConsistentDataEntry(newMessage, this::getMessageByIdentifier).orElseThrow();
    }
    
    public Optional<Message> getMessageByIdentifier(SortableIdentifier identifier) {
        var queryObject = new MessageDao(Message.builder().withIdentifier(identifier).build());
        return attempt(() -> queryObject.fetchByIdentifier(client))
                   .map(Dao::getData)
                   .map(Message.class::cast)
                   .toOptional();
    }
    
    public Message getMessage(UserInstance owner, SortableIdentifier identifier) throws NotFoundException {
        MessageDao queryObject = MessageDao.queryObject(owner, identifier);
        Map<String, AttributeValue> item = fetchMessage(queryObject);
        MessageDao result = parseAttributeValuesMap(item, MessageDao.class);
        return (Message) result.getData();
    }
    
    @Override
    protected AmazonDynamoDB getClient() {
        return client;
    }
    
    private static Supplier<SortableIdentifier> defaultIdentifierSupplier() {
        return SortableIdentifier::next;
    }
    
    private void markTicketReadForSenderAndUnreadForRecipient(TicketEntry ticketEntry, UserInstance sender) {
        if (isOwner(sender, ticketEntry)) {
            ticketEntry.markReadByOwner().markUnreadForCurators().persistUpdate(ticketService);
        } else {
            ticketEntry.markUnreadByOwner().markReadForCurators().persistUpdate(ticketService);
        }
    }
    
    private boolean isOwner(UserInstance sender, TicketEntry ticketEntry) {
        return sender.getUser().equals(ticketEntry.getOwner());
    }
    
    private Message getMessageByIdentifier(Message message) {
        return getMessageByIdentifier(message.getIdentifier()).orElseThrow();
    }
    
    private SortableIdentifier writeMessageToDb(Message message) {
        TransactWriteItem dataWriteItem = newPutTransactionItem(new MessageDao(message));
        
        IdentifierEntry identifierEntry = new IdentifierEntry(message.getIdentifier().toString());
        TransactWriteItem identifierWriteItem = newPutTransactionItem(identifierEntry);
        
        TransactWriteItemsRequest request = newTransactWriteItemsRequest(dataWriteItem, identifierWriteItem);
        sendTransactionWriteRequest(request);
        return message.getIdentifier();
    }
    
    private Map<String, AttributeValue> fetchMessage(MessageDao queryObject) throws NotFoundException {
        
        GetItemRequest getMessageRequest = getMessageByPrimaryKey(queryObject);
        GetItemResult queryResult = client.getItem(getMessageRequest);
        Map<String, AttributeValue> item = queryResult.getItem();
        
        if (isNull(item) || item.isEmpty()) {
            throw new NotFoundException(MESSAGE_NOT_FOUND_ERROR + queryObject.getIdentifier().toString());
        }
        return item;
    }
    
    private Message createMessageEntry(UserInstance sender,
                                       Publication publication,
                                       String messageText,
                                       MessageType messageType) {
        return Message.create(sender,
            publication,
            messageText,
            identifierSupplier.get(),
            clockForTimestamps,
            messageType);
    }
    
    private void requireMessageIsNotBlank(String messageText) {
        if (StringUtils.isBlank(messageText)) {
            throw new InvalidInputException(EMPTY_MESSAGE_ERROR);
        }
    }
    
    private GetItemRequest getMessageByPrimaryKey(MessageDao queryObject) {
        return new GetItemRequest()
                   .withTableName(tableName)
                   .withKey(queryObject.primaryKey());
    }
}
