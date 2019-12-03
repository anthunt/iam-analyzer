package com.anthunt.aws.iam;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.List;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicSessionCredentials;
import com.amazonaws.auth.policy.Action;
import com.amazonaws.auth.policy.Policy;
import com.amazonaws.auth.policy.Statement;
import com.amazonaws.auth.policy.Statement.Effect;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.identitymanagement.AmazonIdentityManagement;
import com.amazonaws.services.identitymanagement.AmazonIdentityManagementClientBuilder;
import com.amazonaws.services.identitymanagement.model.AttachedPolicy;
import com.amazonaws.services.identitymanagement.model.GenerateServiceLastAccessedDetailsRequest;
import com.amazonaws.services.identitymanagement.model.GetPolicyRequest;
import com.amazonaws.services.identitymanagement.model.GetPolicyResult;
import com.amazonaws.services.identitymanagement.model.GetPolicyVersionRequest;
import com.amazonaws.services.identitymanagement.model.GetPolicyVersionResult;
import com.amazonaws.services.identitymanagement.model.GetServiceLastAccessedDetailsRequest;
import com.amazonaws.services.identitymanagement.model.GetServiceLastAccessedDetailsResult;
import com.amazonaws.services.identitymanagement.model.Group;
import com.amazonaws.services.identitymanagement.model.JobStatusType;
import com.amazonaws.services.identitymanagement.model.ListAttachedGroupPoliciesRequest;
import com.amazonaws.services.identitymanagement.model.ListAttachedGroupPoliciesResult;
import com.amazonaws.services.identitymanagement.model.ListAttachedRolePoliciesRequest;
import com.amazonaws.services.identitymanagement.model.ListAttachedRolePoliciesResult;
import com.amazonaws.services.identitymanagement.model.ListAttachedUserPoliciesRequest;
import com.amazonaws.services.identitymanagement.model.ListAttachedUserPoliciesResult;
import com.amazonaws.services.identitymanagement.model.ListGroupsResult;
import com.amazonaws.services.identitymanagement.model.ListRolesResult;
import com.amazonaws.services.identitymanagement.model.ListUsersResult;
import com.amazonaws.services.identitymanagement.model.Role;
import com.amazonaws.services.identitymanagement.model.ServiceLastAccessed;
import com.amazonaws.services.identitymanagement.model.User;
import com.amazonaws.services.organizations.AWSOrganizations;
import com.amazonaws.services.organizations.AWSOrganizationsAsyncClientBuilder;
import com.amazonaws.services.organizations.model.Account;
import com.amazonaws.services.organizations.model.AccountStatus;
import com.amazonaws.services.organizations.model.ListAccountsRequest;
import com.amazonaws.services.organizations.model.ListAccountsResult;
import com.amazonaws.services.securitytoken.AWSSecurityTokenService;
import com.amazonaws.services.securitytoken.AWSSecurityTokenServiceClientBuilder;
import com.amazonaws.services.securitytoken.model.AssumeRoleRequest;
import com.amazonaws.services.securitytoken.model.AssumeRoleResult;
import com.amazonaws.services.securitytoken.model.Credentials;

public class AllowPolicyAnalyzer {

	public static void checkPolicyActions(String accountName, String resourceType, String resourceName, AmazonIdentityManagement amazonIdentityManagement, List<AttachedPolicy> attachedPolicies, List<String> accessedServices) throws IOException {
	
		for(AttachedPolicy attachedPolicy : attachedPolicies) {
			
			GetPolicyResult getPolicyResult = amazonIdentityManagement.getPolicy(
					new GetPolicyRequest()
						.withPolicyArn(attachedPolicy.getPolicyArn())
			);
			
			System.out.println("+++++++++++++++++++++++++++++");
			System.out.println(getPolicyResult.getPolicy().getPolicyName());
			
			GetPolicyVersionResult getPolicyVersionResult = amazonIdentityManagement.getPolicyVersion(
					new GetPolicyVersionRequest()
						.withPolicyArn(getPolicyResult.getPolicy().getArn())
						.withVersionId(getPolicyResult.getPolicy().getDefaultVersionId())
			);
							
			Policy policy = Policy.fromJson(URLDecoder.decode(getPolicyVersionResult.getPolicyVersion().getDocument(), "UTF-8"));
			
			for(Statement statement : policy.getStatements()) {
				if(statement.getEffect() == Effect.Allow) {
					for(Action action : statement.getActions()) {
						
						String[] actionName = action.getActionName().split(":");
						String serviceNamespace = actionName[0];
						if(!accessedServices.contains(serviceNamespace)) {
							writeFile(accountName + "|" + resourceType + "|" + resourceName + "|" + statement.getId() + "|" + statement.getEffect() + "|" + action.getActionName() + "|Unused");
							System.out.println(accountName + "|" + resourceType + "|" + resourceName + "|" + statement.getId() + "|" + statement.getEffect() + "|" + action.getActionName() + "|Unused");	
						} else {
							writeFile(accountName + "|" + resourceType + "|" + resourceName + "|" + statement.getId() + "|" + statement.getEffect() + "|" + action.getActionName() + "|Used");
							System.out.println(accountName + "|" + resourceType + "|" + resourceName + "|" + statement.getId() + "|" + statement.getEffect() + "|" + action.getActionName() + "|Used");
						}
					}
				}
			}
		}
		
	}
	
	private static FileOutputStream dataFileOut;
	
	static {
		try {
			dataFileOut = new FileOutputStream(new File("policy-check.txt"));
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
	}
	
	public static void writeFile(String line) throws IOException {
		line += "\n";
		dataFileOut.write(line.getBytes());
	}
	
	public static List<String> getAccessedServices(AmazonIdentityManagement amazonIdentityManagement, String arn) {
		List<String> accessedServices = new ArrayList<>();
		String jobId = amazonIdentityManagement.generateServiceLastAccessedDetails(
				new GenerateServiceLastAccessedDetailsRequest()
					.withArn(arn)
		).getJobId();
		
		String status;
		
		GetServiceLastAccessedDetailsResult getServiceLastAccessedDetailsResult;
		
		do {
			getServiceLastAccessedDetailsResult = amazonIdentityManagement.getServiceLastAccessedDetails(
					new GetServiceLastAccessedDetailsRequest()
						.withJobId(jobId)
			);						
			
			status = getServiceLastAccessedDetailsResult.getJobStatus();
			
		} while(!JobStatusType.COMPLETED.name().equals(status));
		
		for(ServiceLastAccessed serviceLastAccessed : getServiceLastAccessedDetailsResult.getServicesLastAccessed()) {
			if(serviceLastAccessed.getLastAuthenticated() != null) {
				accessedServices.add(serviceLastAccessed.getServiceNamespace());
			}
		}
		
		return accessedServices;
	}
	
	public static void main(String[] args) throws IOException {
				
		AWSOrganizations awsOrganizations = AWSOrganizationsAsyncClientBuilder.defaultClient();
		
		AWSSecurityTokenService awsSecurityTokenService = AWSSecurityTokenServiceClientBuilder.defaultClient();
		
		ListAccountsResult listAccountsResult = awsOrganizations.listAccounts(new ListAccountsRequest());
		for(Account account : listAccountsResult.getAccounts()) {
			
			System.out.println("=======================================================================================");
			System.out.println(account.getName());
			
			
			if(AccountStatus.ACTIVE.name().equals(account.getStatus()) && !"KEPAYER".equals(account.getName())) {
				try {
					
					AssumeRoleResult assumeRoleResult = awsSecurityTokenService.assumeRole(
							new AssumeRoleRequest()
								.withRoleArn("arn:aws:iam::" + account.getId() + ":role/AWS")
								.withRoleSessionName(account.getName() + "@AWS")
					);
					
					Credentials credentials = assumeRoleResult.getCredentials();
					
					AWSCredentials awsCredentials = new BasicSessionCredentials(
							credentials.getAccessKeyId()
							, credentials.getSecretAccessKey()
							, credentials.getSessionToken()
					);
					
					AmazonIdentityManagement amazonIdentityManagement = AmazonIdentityManagementClientBuilder.standard()
							.withCredentials(new AWSStaticCredentialsProvider(awsCredentials))
							.withRegion(Regions.DEFAULT_REGION)
							.build();
					
					ListGroupsResult listGroupsResult = amazonIdentityManagement.listGroups();
					for(Group group : listGroupsResult.getGroups()) {
						
						System.out.println("------------------------------------------");
						System.out.println(group.getGroupName());
						System.out.println("------------------------------------------");

						List<String> accessedServices = getAccessedServices(amazonIdentityManagement, group.getArn());
						
						ListAttachedGroupPoliciesResult listAttachedGroupPoliciesResult = amazonIdentityManagement.listAttachedGroupPolicies(
								new ListAttachedGroupPoliciesRequest()
									.withGroupName(group.getGroupName())
						);
						
						checkPolicyActions(account.getName(), "Group", group.getGroupName(), amazonIdentityManagement, listAttachedGroupPoliciesResult.getAttachedPolicies(), accessedServices);
					}
					
					ListUsersResult listUsersResult = amazonIdentityManagement.listUsers();
					for(User user : listUsersResult.getUsers()) {
						
						System.out.println("------------------------------------------");
						System.out.println(user.getUserName());
						System.out.println("------------------------------------------");
						
						List<String> accessedServices = getAccessedServices(amazonIdentityManagement, user.getArn());
						
						ListAttachedUserPoliciesResult listAttachedUserPoliciesResult = amazonIdentityManagement.listAttachedUserPolicies(
								new ListAttachedUserPoliciesRequest()
									.withUserName(user.getUserName())
						);
											
						checkPolicyActions(account.getName(), "User", user.getUserName(), amazonIdentityManagement, listAttachedUserPoliciesResult.getAttachedPolicies(), accessedServices);
					}
					
					ListRolesResult listRolesResult = amazonIdentityManagement.listRoles();
					for(Role role : listRolesResult.getRoles()) {
												
						System.out.println("------------------------------------------");
						System.out.println(role.getRoleName());
						System.out.println("------------------------------------------");
						
						List<String> accessedServices = getAccessedServices(amazonIdentityManagement, role.getArn());
						
						ListAttachedRolePoliciesResult listAttachedRolePoliciesResult = amazonIdentityManagement.listAttachedRolePolicies(
								new ListAttachedRolePoliciesRequest()
									.withRoleName(role.getRoleName())
						);
						
						checkPolicyActions(account.getName(), "Role", role.getRoleName(), amazonIdentityManagement, listAttachedRolePoliciesResult.getAttachedPolicies(), accessedServices);
					}
					
				} catch(Exception e) {
					System.out.println("[ERROR] " + e.getMessage());
				}
			}
		}
		
		dataFileOut.close();
	}
}
