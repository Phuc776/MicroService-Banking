**MicroService Banking Application**

**Overview**

This project implements a **Banking Application** using a **Microservice Architecture** to manage various functionalities such as user management, account management, fund transfers, and transaction histories. Each functionality is implemented as a dedicated microservice. This README provides comprehensive documentation for the application, covering its history, architecture, components, and usage.

-----
**Project Origin**

This project is inspired by an existing microservice banking project from the another repository. Initially, the team cloned and studied the project to understand the principles of microservices, including service decoupling, API gateways, and inter-service communication. From there, our team customized and extended the project for better learning and implementation practices.

**Original Grumpy Team Project**

- GitHub Link: [Grumpy Team Banking Project](https://github.com/kartik1502/Spring-Boot-Microservices-Banking-Application)
-----
**Current Project**

**Team Contributions**

This version of the project is a collaborative effort by Team 2, with individual members focusing on specific microservices and infrastructure tasks.

|**Team Member**|**Responsibility**|**Branch**|
| :- | :- | :- |
|**Member 1**|User Service (Authentication, JWT)|user-service|
|**Member 2**|Account Service (Account Management APIs)|account-service|
|**Member 3**|Fund Transfer Service|fund-transfer-service|
|**Member 4**|Transactions Service|transaction-service|
|**Member 5**|Infrastructure (Eureka Server, API Gateway)|eureka-gateway|

-----
**Microservices Architecture**

|Service Name|Description|Port|
| :- | :- | :- |
|**User Service**|Handles user registration, login, JWT authentication, and user management functionality.|8081|
|**Account Service**|Manages user accounts, including account creation, updates, and transaction history.|8082|
|**Fund Transfer**|Enables fund transfers between accounts and provides transfer history.|8083|
|**Transactions**|Provides APIs for viewing transaction histories, deposits, and withdrawals.|8084|
|**Eureka Server**|Service registry for enabling microservice discovery.|8761|
|**API Gateway**|Central entry point for routing requests, securing endpoints with JWT validation.|8080|
|**Sequence Generator**|Generates unique account numbers for new accounts created in the Account Service.|8085|

-----
**Setup Instructions**

**Step 1: Clone the Repository**

Run the following command to clone the repository:

$ git clone git@github.com:hiepdeptrai0908/MicroService-Banking-Team2.git

**Step 2: Checkout Branches**

Navigate to the cloned folder and pull the latest changes from the main branch:

$ cd MicroService-Banking-Team2

$ git checkout main

$ git pull

To work on a specific service, switch to the corresponding branch or create a new branch:

\# Checkout an existing branch

$ git checkout dev/branch\_name

\# Create a new branch

$ git checkout -b dev/new\_branch\_name

**Step 3: Build and Run Services**

Use the following steps to build and start individual services:

1. Navigate to the service directory (e.g., user-service).
1. Build the service:

$ mvn clean install

1. Run the service:

$ mvn spring-boot:run

-----
**Detailed Service Information**

**1. User Service**

- **Responsibilities:** Handles user registration, login, and JWT-based authentication.
- **Key Endpoints:**
  - POST /users/register: Register a new user.
  - POST /users/login: Authenticate a user and generate a JWT token.
  - GET /users/profile: Retrieve user profile details.
- **Port:** 8081

**2. Account Service**

- **Responsibilities:** Manages account creation, updates, and details.
- **Key Endpoints:**
  - POST /accounts/create: Create a new account.
  - GET /accounts/{id}: View account details.
  - PUT /accounts/{id}: Update account information.
- **Port:** 8082

**3. Fund Transfer Service**

- **Responsibilities:** Handles transfers between accounts and transfer histories.
- **Key Endpoints:**
  - POST /transfers/initiate: Initiate a fund transfer.
  - GET /transfers/history: Retrieve transfer history.
- **Port:** 8083

**4. Transactions Service**

- **Responsibilities:** Provides transaction records, deposit, and withdrawal functionality.
- **Key Endpoints:**
  - POST /transactions/deposit: Deposit funds into an account.
  - POST /transactions/withdraw: Withdraw funds from an account.
  - GET /transactions/history: Retrieve transaction history.
- **Port:** 8084

**5. Eureka Server**

- **Responsibilities:** Acts as a service registry for microservices.
- **Port:** 8761

**6. API Gateway**

- **Responsibilities:** Secures and routes requests to appropriate microservices using JWT validation.
- **Port:** 8080

**7. Sequence Generator Service**

- **Responsibilities:** Generates unique account numbers for new accounts in the Account Service.
- **Port:** 8085
-----
**Technology Stack**

- **Programming Language:** Java (Spring Boot)
- **Authentication:** JSON Web Token (JWT)
- **Service Discovery:** Eureka Server
- **Gateway:** Spring Cloud Gateway
- **Database:** MySQL
- **Build Tool:** Maven
-----
**License**

This project is licensed under the MIT License. See the LICENSE file for details.
