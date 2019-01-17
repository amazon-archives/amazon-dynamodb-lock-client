/**
 * Copyright 2013-2017 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * <p>
 * Licensed under the Amazon Software License (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 * <p>
 * http://aws.amazon.com/asl/
 * <p>
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, express
 * or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 */
package com.amazonaws.services.dynamodbv2;

import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.Before;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder.EndpointConfiguration;
import com.amazonaws.services.dynamodbv2.model.ProvisionedThroughput;

/**
 * Base class for lock client tests. Sets up in-memory version of DynamoDB.
 *
 * @author <a href="mailto:slutsker@amazon.com">Sasha Slutsker</a>
 * @author <a href="mailto:amcp@amazon.co.jp">Alexander Patrikalakis</a>
 */
public class InMemoryLockClientTester {
    protected static final String LOCALHOST = "mars";
    protected static final String INTEGRATION_TESTER = "integrationTester";
    protected static final SecureRandom SECURE_RANDOM;

    static {
        SECURE_RANDOM = new SecureRandom();
    }

    protected static final String TABLE_NAME = "test";
    protected static final String RANGE_KEY_TABLE_NAME = "rangeTest";
    protected static final String TEST_DATA = "test_data";
    protected static final long SHORT_LEASE_DUR = 60L; //60 milliseconds

    protected static final AcquireLockOptions ACQUIRE_LOCK_OPTIONS_TEST_KEY_1 = AcquireLockOptions.builder("testKey1").withData(ByteBuffer.wrap(TEST_DATA.getBytes())).build();

    protected static final AcquireLockOptions ACQUIRE_LOCK_OPTIONS_REPLACE_DATA_FALSE =
        AcquireLockOptions.builder("testKey1").withData(ByteBuffer.wrap(TEST_DATA.getBytes())).withReplaceData(false).build();

    protected static final AcquireLockOptions ACQUIRE_LOCK_OPTIONS_TEST_KEY_2 = AcquireLockOptions.builder("testKey2").withData(ByteBuffer.wrap(TEST_DATA.getBytes())).build();

    protected static final AcquireLockOptions ACQUIRE_LOCK_OPTIONS_WITH_NO_WAIT =
        AcquireLockOptions.builder("testKey1").withData(ByteBuffer.wrap(TEST_DATA.getBytes())).withAdditionalTimeToWaitForLock(0L).withRefreshPeriod(0L)
            .withTimeUnit(TimeUnit.SECONDS).build();

    protected static final AcquireLockOptions ACQUIRE_LOCK_OPTIONS_5_SECONDS =
        AcquireLockOptions.builder("testKey1").withData(ByteBuffer.wrap(TEST_DATA.getBytes())).withAdditionalTimeToWaitForLock(5L).withRefreshPeriod(1L)
            .withTimeUnit(TimeUnit.SECONDS).build();

    protected static final GetLockOptions GET_LOCK_OPTIONS_DELETE_ON_RELEASE = GetLockOptions.builder("testKey1").withDeleteLockOnRelease(true).build();

    protected static final GetLockOptions GET_LOCK_OPTIONS_DO_NOT_DELETE_ON_RELEASE = GetLockOptions.builder("testKey1").withDeleteLockOnRelease(false).build();

    protected AmazonDynamoDB dynamoDBMock;
    protected AmazonDynamoDBLockClient lockClient;
    protected AmazonDynamoDBLockClient lockClientWithHeartbeating;
    protected AmazonDynamoDBLockClient lockClientWithSequenceIdTracking;
    protected AmazonDynamoDBLockClient shortLeaseLockClient;
    protected AmazonDynamoDBLockClient lockClientNoTable;
    protected AmazonDynamoDBLockClientOptions lockClient1Options;

    @Before
    public void setUp() {
        final AWSCredentials credentials = new BasicAWSCredentials(TABLE_NAME, "");
        final String endpoint = System.getProperty("dynamodb-local.endpoint");

        this.dynamoDBMock =
            AmazonDynamoDBClientBuilder.standard().withCredentials(new AWSStaticCredentialsProvider(credentials)).withEndpointConfiguration(new EndpointConfiguration(endpoint, ""))
                .build();

        AmazonDynamoDBLockClient.createLockTableInDynamoDB(
            CreateDynamoDBTableOptions.builder(this.dynamoDBMock, new ProvisionedThroughput().withReadCapacityUnits(10L).withWriteCapacityUnits(10L), TABLE_NAME).build());
        AmazonDynamoDBLockClient.createLockTableInDynamoDB(
            CreateDynamoDBTableOptions.builder(this.dynamoDBMock, new ProvisionedThroughput().withReadCapacityUnits(10L).withWriteCapacityUnits(10L), RANGE_KEY_TABLE_NAME)
                .withSortKeyName("rangeKey").build());

        this.lockClient1Options =
            new AmazonDynamoDBLockClientOptions.AmazonDynamoDBLockClientOptionsBuilder(this.dynamoDBMock, TABLE_NAME, INTEGRATION_TESTER).withLeaseDuration(3L).withHeartbeatPeriod(1L)
                .withTimeUnit(TimeUnit.SECONDS).withCreateHeartbeatBackgroundThread(false).build();
        this.lockClient = new AmazonDynamoDBLockClient(this.lockClient1Options);
        this.lockClientWithHeartbeating = new AmazonDynamoDBLockClient(
            new AmazonDynamoDBLockClientOptions.AmazonDynamoDBLockClientOptionsBuilder(this.dynamoDBMock, TABLE_NAME, INTEGRATION_TESTER).withLeaseDuration(3L).withHeartbeatPeriod(1L)
                .withTimeUnit(TimeUnit.SECONDS).withCreateHeartbeatBackgroundThread(true).build());
        this.lockClientWithSequenceIdTracking = new AmazonDynamoDBLockClient(
            new AmazonDynamoDBLockClientOptions.AmazonDynamoDBLockClientOptionsBuilder(this.dynamoDBMock, TABLE_NAME, INTEGRATION_TESTER).withLeaseDuration(3L).withHeartbeatPeriod(1L)
                .withTimeUnit(TimeUnit.SECONDS).withCreateHeartbeatBackgroundThread(false).withSequenceIdTracking(true).build());
        this.lockClientNoTable = new AmazonDynamoDBLockClient(
            new AmazonDynamoDBLockClientOptions.AmazonDynamoDBLockClientOptionsBuilder(this.dynamoDBMock, "doesNotExist", INTEGRATION_TESTER).withLeaseDuration(3L).withHeartbeatPeriod(1L)
                .withTimeUnit(TimeUnit.SECONDS).withCreateHeartbeatBackgroundThread(false).build());
        this.shortLeaseLockClient = new AmazonDynamoDBLockClient(
            new AmazonDynamoDBLockClientOptions.AmazonDynamoDBLockClientOptionsBuilder(this.dynamoDBMock, TABLE_NAME, INTEGRATION_TESTER).withLeaseDuration(SHORT_LEASE_DUR)
                .withHeartbeatPeriod(10L).withTimeUnit(TimeUnit.MILLISECONDS).withCreateHeartbeatBackgroundThread(false).build());
    }

    @After
    public void deleteTables() {
        this.dynamoDBMock.deleteTable(TABLE_NAME);
        this.dynamoDBMock.deleteTable(RANGE_KEY_TABLE_NAME);
    }
}
