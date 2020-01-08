package com.anthunt.aws.iam;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import com.amazonaws.regions.Regions;
import com.amazonaws.services.cloudwatch.AmazonCloudWatch;
import com.amazonaws.services.cloudwatch.AmazonCloudWatchClientBuilder;
import com.amazonaws.services.cloudwatch.model.Dimension;
import com.amazonaws.services.cloudwatch.model.MetricDatum;
import com.amazonaws.services.cloudwatch.model.PutMetricDataRequest;
import com.amazonaws.services.cloudwatch.model.PutMetricDataResult;
import com.amazonaws.services.cloudwatch.model.StandardUnit;
import com.amazonaws.services.logs.AWSLogs;
import com.amazonaws.services.logs.AWSLogsClientBuilder;
import com.amazonaws.services.logs.model.CreateLogGroupRequest;
import com.amazonaws.services.logs.model.CreateLogGroupResult;
import com.amazonaws.services.logs.model.CreateLogStreamRequest;
import com.amazonaws.services.logs.model.CreateLogStreamResult;
import com.amazonaws.services.logs.model.InputLogEvent;
import com.amazonaws.services.logs.model.PutLogEventsRequest;

public class CustomMetric {

	
	public static void main(String[] args) {

		//AmazonCloudWatch amazonCloudWatch = AmazonCloudWatchClientBuilder.defaultClient();
		
//		AmazonCloudWatch amazonCloudWatch = AmazonCloudWatchClientBuilder.standard()
//				.withCredentials(new MergedProfileCredentialsProvider("KEAPPMIG"))
//				.withRegion(Regions.AP_NORTHEAST_2)
//				.build();
//		
//		//aws cloudwatch put-metric-data --metric-name PageViewCount --namespace "MyService" --value 2 --timestamp 2016-01-15T12:00:00.000Z
//		
//		List<Dimension> dimensions = new ArrayList<>();
//		dimensions.add(new Dimension().withName("Source Service").withValue("AAAA"));
//		dimensions.add(new Dimension().withName("Target Service").withValue("BBBB"));
//		
//		MetricDatum metricData = new MetricDatum();
//		metricData.setMetricName("SampleMetric");
//		metricData.setValue(Double.parseDouble("10"));
//		metricData.setDimensions(dimensions);
//		metricData.setUnit(StandardUnit.Count);
//		metricData.setTimestamp(new Date());
//		
//		PutMetricDataResult putMetricDataResult = amazonCloudWatch.putMetricData(
//				new PutMetricDataRequest()
//					.withMetricData(metricData)
//					.withNamespace("CustomService")
//		);
		
		
		AWSLogs awsLogs = AWSLogsClientBuilder.standard()
				.withCredentials(new MergedProfileCredentialsProvider("KEAPPMIG"))
				.withRegion(Regions.AP_NORTHEAST_2)
				.build();
		
		
		// Log Group 생성
		CreateLogGroupResult createLogGroupResult = awsLogs.createLogGroup(
				new CreateLogGroupRequest("CustomerMetricLogGroup")
		);
		
		
		// Log Stream 생성
		CreateLogStreamResult createLogStreamResult = awsLogs.createLogStream(
				new CreateLogStreamRequest("CustomerMetricLogGroup", "Stream1")
		);
		
		//
		List<InputLogEvent> inputLogEvents = new ArrayList<>();
		inputLogEvents.add(new InputLogEvent().withMessage("Log1").withTimestamp(new Date().getTime()));
		inputLogEvents.add(new InputLogEvent().withMessage("Log2").withTimestamp(new Date().getTime()));
		inputLogEvents.add(new InputLogEvent().withMessage("Log3").withTimestamp(new Date().getTime()));
		inputLogEvents.add(new InputLogEvent().withMessage("Log4").withTimestamp(new Date().getTime()));
		inputLogEvents.add(new InputLogEvent().withMessage("Log5").withTimestamp(new Date().getTime()));
		inputLogEvents.add(new InputLogEvent().withMessage("Log6").withTimestamp(new Date().getTime()));
		inputLogEvents.add(new InputLogEvent().withMessage("Log7").withTimestamp(new Date().getTime()));
		
		
		awsLogs.putLogEvents(
				new PutLogEventsRequest()
					.withLogGroupName("CustomerMetricLogGroup")
					.withLogStreamName("Stream1")
					.withLogEvents(inputLogEvents)
		);
	}
	
	
}
