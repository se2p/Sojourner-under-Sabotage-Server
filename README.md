# Sojourner under Sabotage - Server
## Create the Unity build
Build the Unity project with WebGL as the target platform. 
Use `/src/main/resources/static/unity/` as build output directory.  
The source code or a prebuilt export can be found in the [Sojourner-under-Sabotage-Game](../../../Sojourner-under-Sabotage-Game) repository.

## Webpack Install and Build
- switch to `/src/main/js/`
- install dependencies `npm install`
- execute webpack `npm run build`

## Install and Run the Server
- Install dependencies `mvn install`
- Copy `example.application.properties` to `application.properties` and add your database credentials 
  (port and db_name in the URL, username and password below).
- Run the application `mvn spring-boot:run`

Now the server runs and the frontend can be accessed at the port configured in the `application.properties`.


## Common Deployment Errors and Solutions
- Duplicate mapping after renaming/moving a controller ➡ `mvn clean`
- Package org.junit.jupiter.api does not exist ➡ update the `jarsToInclude` setting in the application.properties
- The `localhost.date.log` file is nearly empty ➡ look into `catalina.out`

## Test the API
```shell
curl localhost:8080/api/hello -i
```


```shell
curl -H "Accept: application/json" -H "Content-Type: application/json" -X POST localhost:8080/api/auth -d  "{\"login\":\"user@mail.com\",\"password\":\"1234\"} -i
````

```shell
curl -H "Accept: application/json" -H "Content-Type: application/json" -X POST -d "{\"className\":\"DemoTest\",\"sourceCode\":\"public class DemoTest{@org.junit.Test public void testDemoAddition(){org.junit.Assert.assertEquals(3, Demo.add(2, 1));}}\",\"cutComponentName\":\"Demo\"}" -i -H "Authorization: Bearer insert_valid_token_here" localhost:8080/api/execute
```
