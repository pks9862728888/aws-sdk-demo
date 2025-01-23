package com.demo.awssdkdemo.services;

import com.demo.awssdkdemo.configurations.AwsSdkConfigParams;
import com.demo.awssdkdemo.serializer.CustomZonedDateTimeSerializer;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.openlineage.client.OpenLineage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.datazone.DataZoneClient;
import software.amazon.awssdk.services.datazone.model.CreateAssetRequest;
import software.amazon.awssdk.services.datazone.model.CreateAssetResponse;
import software.amazon.awssdk.services.datazone.model.CreateAssetRevisionRequest;
import software.amazon.awssdk.services.datazone.model.CreateAssetRevisionResponse;
import software.amazon.awssdk.services.datazone.model.FormInput;
import software.amazon.awssdk.services.datazone.model.GetAssetRequest;
import software.amazon.awssdk.services.datazone.model.GetAssetResponse;
import software.amazon.awssdk.services.datazone.model.GetAssetTypeRequest;
import software.amazon.awssdk.services.datazone.model.GetAssetTypeResponse;
import software.amazon.awssdk.services.datazone.model.GetLineageEventRequest;
import software.amazon.awssdk.services.datazone.model.GetLineageEventResponse;
import software.amazon.awssdk.services.datazone.model.ListProjectsRequest;
import software.amazon.awssdk.services.datazone.model.ListProjectsResponse;
import software.amazon.awssdk.services.datazone.model.PostLineageEventRequest;
import software.amazon.awssdk.services.datazone.model.PostLineageEventResponse;
import software.amazon.awssdk.services.datazone.model.ProjectSummary;
import software.amazon.awssdk.services.datazone.model.ResourceNotFoundException;

import java.net.URI;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class AwsDatazoneService {
    private final DataZoneClient dataZoneClient;
    private final AwsSdkConfigParams awsSdkConfigParams;

    public ListProjectsResponse listProjects() {
        log.info("Listing projects...");
        ListProjectsResponse listProjectsResponse = dataZoneClient.listProjects(ListProjectsRequest.builder()
                .domainIdentifier(awsSdkConfigParams.getDomainIdentifier())
                .build());
        log.info("List Project response: {}", listProjectsResponse.toString());
        return listProjectsResponse;
    }

    public Optional<String> getProjectId(String projectName) {
        log.info("GetProject id: {}", projectName);
        return listProjects()
                .items()
                .stream()
                .filter(p -> p.name().equalsIgnoreCase(projectName))
                .map(ProjectSummary::id)
                .findFirst();
    }

    public void getAsset(String assetId) {
        log.info("Finding asset by assetId: {}", assetId);
        GetAssetResponse getAssetResponse = dataZoneClient.getAsset(GetAssetRequest.builder()
                .domainIdentifier(awsSdkConfigParams.getDomainIdentifier())
                .identifier(assetId)
                .build());
        log.info("GetAsset response: {}", getAssetResponse);
    }

    public void createAsset(
            String assetName, String assetType, String owningProject, List<String> glossaryTerms,
            List<FormInput> metaDataForms) {
        String owningProjectId = getProjectId(owningProject)
                .orElseThrow(() -> new RuntimeException("Project not found: " + owningProject));
        String assetTypeId = getAssetTypeId(assetType)
                .orElseThrow(() -> new RuntimeException("Asset type not found: " + assetType));
        log.info("Creating asset...");
        CreateAssetRequest createAssetRequest = CreateAssetRequest.builder()
                .domainIdentifier(awsSdkConfigParams.getDomainIdentifier())
                .name(assetName)
                .owningProjectIdentifier(owningProjectId)
                .description("Test asset creation: " + assetName)
                .formsInput(metaDataForms)
                .typeIdentifier(assetTypeId)
                .build();
        if (!glossaryTerms.isEmpty()) {
            createAssetRequest = createAssetRequest.toBuilder()
                    .glossaryTerms(glossaryTerms)
                    .build();
        }
        CreateAssetResponse createAssetResponse = dataZoneClient.createAsset(createAssetRequest);
        log.info("CreateAsset response: {}", createAssetResponse);
    }

    public void updateAsset(String assetName, String assetId, List<FormInput> metaDataForms) {
        // This is POST request (i.e updates everything)
        log.info("Updating asset: {}", assetName);
        CreateAssetRevisionRequest createAssetRevision = CreateAssetRevisionRequest.builder()
                .domainIdentifier(awsSdkConfigParams.getDomainIdentifier())
                .name(assetName)
                .description("Test asset creation: " + assetName)
                .identifier(assetId)
                .formsInput(metaDataForms)
                .build();
        CreateAssetRevisionResponse createAssetRevisionRequest =
                dataZoneClient.createAssetRevision(createAssetRevision);
        log.info("CreateAssetRevision response: {}", createAssetRevisionRequest);
    }

    public void postLineageEvent(String sourceAssetId, String targetAssetId) throws JsonProcessingException {
        log.info("Posting lineage event: {} -> {}", sourceAssetId, targetAssetId);
        OpenLineage openLineage = new OpenLineage(URI.create(""));
        OpenLineage.RunEvent openLineageRunEvent = openLineage.newRunEventBuilder()
                .job(openLineage.newJobBuilder()
                        .name("DatazoneLineageJob")
                        .namespace(awsSdkConfigParams.getDomainIdentifier())
                        .build())
                .run(openLineage.newRunBuilder()
                        .runId(UUID.randomUUID())
                        .build())
                .eventType(OpenLineage.RunEvent.EventType.COMPLETE)
                .eventTime(ZonedDateTime.now())
                .inputs(List.of(openLineage.newInputDataset(awsSdkConfigParams.getDomainIdentifier(),
                        sourceAssetId, null, null)))
                .outputs(List.of(openLineage.newOutputDataset(awsSdkConfigParams.getDomainIdentifier(),
                        targetAssetId, null, null)))
                .build();

        ObjectMapper objectMapper = getObjectMapper();
        String runEvent = objectMapper.writeValueAsString(openLineageRunEvent);
        log.info("{}", runEvent);

        // Post lineage event
        PostLineageEventRequest postLineageEventRequest = PostLineageEventRequest.builder()
                .domainIdentifier(awsSdkConfigParams.getDomainIdentifier())
                .clientToken(UUID.randomUUID().toString())
                .event(SdkBytes.fromUtf8String(runEvent))
                .build();
        PostLineageEventResponse postLineageEventResponse = dataZoneClient.postLineageEvent(postLineageEventRequest);
        log.info("PostLineageEvent response: {}", postLineageEventResponse);
    }

    private static ObjectMapper getObjectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();
        JavaTimeModule javaTimeModule = new JavaTimeModule();
        // Add a LocalDateTimeDeserializer with the ISO-8601 format
        javaTimeModule.addSerializer(ZonedDateTime.class, new CustomZonedDateTimeSerializer());
        objectMapper.registerModule(javaTimeModule);
        objectMapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        return objectMapper;
    }

    private Optional<String> getAssetTypeId(String assetType) {
        log.info("Finding asset type: {}", assetType);
        GetAssetTypeResponse assetTypeResponse = dataZoneClient.getAssetType(GetAssetTypeRequest.builder()
                .domainIdentifier(awsSdkConfigParams.getDomainIdentifier())
                .identifier(assetType)
                .build());
        log.info("GetAssetType response: {}", assetTypeResponse);
        return Optional.ofNullable(assetTypeResponse.name());
    }

    public boolean assetTypeExists(String assetType) {
        try {
            log.info("Checking if asset type exists: {}", assetType);
            GetAssetTypeResponse assetTypeResponse = dataZoneClient.getAssetType(GetAssetTypeRequest.builder()
                    .domainIdentifier(awsSdkConfigParams.getDomainIdentifier())
                    .identifier(assetType)
                    .build());
            log.info("GetAssetType response: {}", assetTypeResponse);
            return true;
        } catch (ResourceNotFoundException r) {
            return false;
        }
    }

    public void getLineageEvent(String lineageEventId) {
        // Need to know lineage event id
        GetLineageEventResponse getLineageEvent = dataZoneClient.getLineageEvent(GetLineageEventRequest.builder()
                .domainIdentifier(awsSdkConfigParams.getDomainIdentifier())
                .identifier(lineageEventId)
                .build());
        log.info("GetLinageEvent response: {}", getLineageEvent);
    }
}
