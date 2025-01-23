package com.demo.awssdkdemo;

import com.demo.awssdkdemo.services.AwsDatazoneService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import software.amazon.awssdk.services.datazone.model.FormInput;

import java.util.Collections;
import java.util.List;

@SpringBootApplication
@RequiredArgsConstructor
@Slf4j
public class AwsSdkDemoApplication implements CommandLineRunner {
    private final AwsDatazoneService awsDatazoneService;

    public static final String PROJECT_NAME = "producer-project";

    public static void main(String[] args) {
//        System.setProperty(SdkSystemSetting.AWS_DISA)
        SpringApplication.run(AwsSdkDemoApplication.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
//        awsDatazoneService.listProjects();
//        log.info("{}", awsDatazoneService.getProjectId(PROJECT_NAME));
//        log.info("AssetType exists: {}", awsDatazoneService.assetTypeExists("JsonAssetType"));
//        createDepartmentAsset();
//        updateDepartmentAsset();
//        awsDatazoneService.getAsset("c0vdcl6vwvjr4y");//"Department1");
        awsDatazoneService.getLineageEvent("3jsqbte83xrjqa");
//        awsDatazoneService.postLineageEvent("c0vdcl6vwvjr4y", // Department1
//                "563t51p703os9u" // Output1
//        );
        System.exit(0);
    }

    private void updateDepartmentAsset() {
        awsDatazoneService.updateAsset("TestDepartmentAsset", "bb6qulorb02wiq",
                List.of(FormInput.builder()
                        .formName("DepartmentMetadataForm")
                        .typeIdentifier("DepartmentMetaDataForm")
                        .build()));
    }

    private void createDepartmentAsset() {
        awsDatazoneService.createAsset("TestDepartmentAsset", "JsonAssetType", PROJECT_NAME,
                Collections.emptyList(), //List.of("DepartmentGlossary"),
                List.of(FormInput.builder()
                        .formName("DepartmentMetadataForm")
                        .typeIdentifier("DepartmentMetaDataForm")
                        .build()));
    }
}
