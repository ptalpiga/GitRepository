/* $Header: mos/source/ip/common/RestUi/src/oracle/mos/rest/sp/SpSrCreateRestService.java /main/11 2014/05/15 02:57:34 eholobiu Exp $ */

/* Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.*/

/*
   DESCRIPTION
    REST services for SR Create functionality.

   MODIFIED    (MM/DD/YY)
    eholobiu    02/18/14 - Creation
 */

/**
 *  @version $Header: mos/source/ip/common/RestUi/src/oracle/mos/rest/sp/SpSrCreateRestService.java /main/11 2014/05/15 02:57:34 eholobiu Exp $
 *  @author  eholobiu
 *  @since   14.3
 */

package oracle.mos.rest.sp;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

import javax.ws.rs.core.Response;

import oracle.mos.fwk.exception.BusinessException;
import oracle.mos.fwk.exception.ServiceException;
import oracle.mos.fwk.foundation.Maf;
import oracle.mos.fwk.foundation.logging.ILogger;
import oracle.mos.sp.model.Account;
import oracle.mos.sp.model.AccountClassification;
import oracle.mos.sp.model.Address;
import oracle.mos.sp.model.ListOfValues;
import oracle.mos.sp.model.PagedFilter;
import oracle.mos.sp.model.PagedResult;
import oracle.mos.sp.model.SR;
import oracle.mos.sp.model.srcreate.CreateSrResult;
import oracle.mos.sp.model.srcreate.Csi;
import oracle.mos.sp.model.srcreate.ProductTemplate;
import oracle.mos.sp.model.srcreate.ProductTemplateClarification;
import oracle.mos.sp.model.srcreate.SRHandlingInstruction;
import oracle.mos.sp.services.SrCreateServiceLocal;
import oracle.mos.sp.services.SrServiceLocator;
import oracle.mos.sp.util.ErrorCodes;
import oracle.mos.sp.util.StringUtils;


@Path("1.0/ServiceRequests/srCreate")
public class SpSrCreateRestService extends SpRestBase {

  private static final ILogger LOGGER = Maf.getLogger(SpSrCreateRestService.class);

  /**
   * Returns a JSON representation of a Csis object (which contains a list of csi and optionally, record count)
   * filtered by one of (or a combination of) csiNumber, csiName, accountNumber, accountName, country
   * and paginated according to offset and limit.
   * Data can be filtered by both exact match and wildcard search (wild character used for search must be specified in the parameter value)
   *
   * @param csiNumber
   * @param csiName
   * @param accountNumber
   * @param accountName
   * @param country
   * @param contactEmail
   * @param offset first row to be returned, 0 based
   * @param limit number of rows to be returned
   * @param orderBy specifies the order by clause. The value of this query parameter is fieldName[":"("asc"/"desc")].
   *                If there are multiple fields, then comma "," is used to separate them, such as "?orderBy=dname:asc,loc:desc".
   *                Valid fieldNames must are: csiNumber, csiName, accountName, accountNumber, alternateAccountName, country, location,
   *                derivedStatus, hardwareSoftware.
   * @param totalResults true if record count is needed.
   * @return a Csis JSON object (which contains a list of csi and optionally, record count)
   * @throws ServiceException if backend is not available
   * @throws BusinessException if:
   *          no csi filter (csiNumber, csiName, accountNumber, accountName, country, contactEmail) is specified
   *          offset is negative
   *          limit is < 0 or > max number of records that can be returned by backend
   *          orderBy format is invalid or fieldNames are not a valid sort column (see orderBy for valid sort columns)
   *          no data is returned by backend.
   */
  @GET
  @Produces(MediaType.APPLICATION_JSON + ";charset=utf-8")
  @Path("/csis")
  public PagedResult<Csi> getCsis(@QueryParam("csiNumber") String csiNumber, 
                                  @QueryParam("csiName") String csiName, 
                                  @QueryParam("accountNumber") String accountNumber, 
                                  @QueryParam("accountName") String accountName, 
                                  @QueryParam("country") String country, 
                                  @QueryParam("contactEmail") String contactEmail, 
                                  @QueryParam("orderBy") String orderBy, 
                                  @QueryParam("offset") String offset, 
                                  @QueryParam("limit") String limit, 
                                  @QueryParam("totalResults") String totalResults) throws BusinessException, ServiceException {

    if (StringUtils.isEmpty(csiNumber) && StringUtils.isEmpty(csiName) && StringUtils.isEmpty(accountNumber) &&
        StringUtils.isEmpty(accountName) && StringUtils.isEmpty(country) && StringUtils.isEmpty(contactEmail)) {
      LOGGER.warning("Missing mandatory query param (csiNumber, csiName, accountNumber, accountName, country or contactEmail) was specified");
      throw new BusinessException(ErrorCodes.INVALID_ARGUMENT,
                                  "At least one of csiNumber, csiName, accountNumber, accountName, country or contactEmail must be specified");
    }

    Csi filterFields = new Csi();
    filterFields.setCsiNumber(csiNumber);
    filterFields.setCsiName(csiName);
    filterFields.setAccountNumber(accountNumber);
    filterFields.setAccountName(accountName);
    filterFields.setCountry(country);
    filterFields.setContactEmail(contactEmail);

    PagedFilter<Csi> filter =
      new PagedFilter<Csi>(filterFields, parseOffset(offset), parseLimit(limit), getSortCriteria(orderBy),
                           "true".equalsIgnoreCase(totalResults));

    PagedResult<Csi> result = null;
    try {
      result = SrServiceLocator.getSrCreateService().getCsis(filter);
      if (result == null) {
        LOGGER.info("No results retuned by backend");
        throw new BusinessException(ErrorCodes.NO_DATA, "No csis returned");
      }
      return result;
    } catch (BusinessException be) {
      throw be;
    } catch(ServiceException se) {
      throw se;
    } catch(Exception e) {
      LOGGER.severe("Unexpected error occured while returning backend data.");
      throw new ServiceException(ErrorCodes.INTERNAL_ERROR, e.getMessage());
    }
  }

  /**
   * Returns a JSON representation of a PagedResult object (which contains a <b>list of {@link SRHandlingInstruction}</b> and optionally,
   * record count) filtered by one of (or a combination of) <b>activeFlag, status, endDate, hardwareSoftware, accountId,
   * csiId</b> and paginated according to offset and limit params.
   * Data can be filtered by both exact match and wildcard search (wild character used for search must be specified
   * in the parameter value)
   *
   * @param activeFlag must have one of the following values: 'Y' if the active flag is true and 'N' otherwise
   * @param status
   * @param hardwareSoftware can be one of the: "hardwareSoftware", "hardware" or "software"
   * @param accountId the account identifier for which the list of handling instructions should be returned
   * @param csiId the CSI identifier for which the list of handling instructions should be returned
   * @param orderBy specifies the order by clause. The value of this query parameter is fieldName[":"("asc"/"desc")].
   *         If there are multiple fields, then comma "," is used to separate them, such as "?orderBy=dname:asc,loc:desc".
   *         Valid fieldNames must are: <i>activeFlag</i>, <i>status</i>, <i>endDate</i>, <i>hardwareSoftware</i>, <i>accountId</i>,
   *         <i>csiId</i>.
   * @param offset first row to be returned, zero based
   * @param limit number of rows to be returned
   * @param totalResults true if record count is needed.
   * @return a PagedResult JSON object (which contains a <b>list of {@link SRHandlingInstruction}</b> and optionally, record count)
   * @throws ServiceException if backend is not available
   * @throws BusinessException if:
   *          no srhi filter (<i>activeFlag</i>, <i>status</i>, <i>endDate</i>, <i>hardwareSoftware</i>, <i>accountId</i>,
   *          <i>csiId</i>) is specified
   *          offset is negative
   *          limit is < 0 or > max number of records that can be returned by backend
   *          orderBy format is invalid or fieldNames are not a valid sort column (see orderBy for valid sort columns)
   *          no data is returned by backend.
   */
  @GET
  @Produces(MediaType.APPLICATION_JSON + ";charset=utf-8")
  @Path("/srhi")
  public PagedResult<SRHandlingInstruction> getSrhi(@QueryParam("activeFlag") String activeFlag, 
                                                    @QueryParam("status") String status, 
                                                    @QueryParam("hardwareSoftware") String hardwareSoftware, 
                                                    @QueryParam("accountId") String accountId, 
                                                    @QueryParam("csiId") String csiId, 
                                                    @QueryParam("productId") String productId,
                                                    @QueryParam("orderBy") String orderBy, 
                                                    @QueryParam("offset") String offset, 
                                                    @QueryParam("limit") String limit, 
                                                    @QueryParam("totalResults") String totalResults) 
                                                    throws BusinessException, ServiceException {

    if (StringUtils.isEmpty(activeFlag) || StringUtils.isEmpty(status) || StringUtils.isEmpty(hardwareSoftware) ||
        (StringUtils.isEmpty(accountId) && (StringUtils.isEmpty(csiId) && (StringUtils.isEmpty(productId))))) {
      LOGGER.warning("Missing mandatory query param (activeFlag, status, endDate, hardwareSoftware, accountId, csiId or productId) was not specified");
      throw new BusinessException(ErrorCodes.INVALID_ARGUMENT,
                                  "All of the activeFlag, status, endDate, hardwareSoftware, accountId, csiId or productId params must be specified");
    }

    SRHandlingInstruction filterFields = new SRHandlingInstruction();
    filterFields.setActiveFlag(activeFlag);
    filterFields.setStatus(status);
    filterFields.setHardwareSoftware(hardwareSoftware);
    filterFields.setAccountId(accountId);
    filterFields.setCsiId(csiId);
    filterFields.setProductId(productId);

    PagedFilter<SRHandlingInstruction> filter =
      new PagedFilter<SRHandlingInstruction>(filterFields, parseOffset(offset), parseLimit(limit),
                                             getSortCriteria(orderBy), "true".equalsIgnoreCase(totalResults));

    PagedResult<SRHandlingInstruction> result = null;
    try {
      result = SrServiceLocator.getSrCreateService().getSrhis(filter);
      if (result == null) {
        LOGGER.info("No results retuned by backend");
        throw new BusinessException(ErrorCodes.NO_DATA, "No srhis returned");
      }
      return result;
    } catch (BusinessException be) {
      throw be;
    } catch(ServiceException se) {
      throw se;
    } catch(Exception e) {
      LOGGER.severe("Unexpected error occured while returning backend data.");
      throw new ServiceException(ErrorCodes.INTERNAL_ERROR, e.getMessage());
    }
  }
  
  /**
   * Returns a JSON representation of Account details for the given accountId
   * @param accountId
   * @return JSON representation of the account details for the given accountId
   * @throws ServiceException if backend is not available
   * @throws BusinessException if:
   *                            accountId is empty
   *                            no data is returned by backend.          
   */
  @GET
  @Produces(MediaType.APPLICATION_JSON + ";charset=utf-8")
  @Path("/accountInfo")
  public Account getAccountInfo(@QueryParam("accountId") String accountId) throws BusinessException, ServiceException {
    if (StringUtils.isEmpty(accountId)) {
      LOGGER.warning("Missing mandatory query param: accountId");
      throw new BusinessException(ErrorCodes.INVALID_ARGUMENT, "AccountId is mandatory");
    }

    Account result = null;
    try {
      SrCreateServiceLocal service = SrServiceLocator.getSrCreateService();
      result = service.getAccountInfo(accountId);
      if (result == null) {
        LOGGER.info("No results retuned by backend");
        throw new BusinessException(ErrorCodes.NO_DATA, "No account info returned");
      }
      return result;
    } catch (BusinessException be) {
      throw be;
    } catch(ServiceException se) {
      throw se;
    } catch(Exception e) {
      LOGGER.severe("Unexpected error occured while returning backend data.");
      throw new ServiceException(ErrorCodes.INTERNAL_ERROR, e.getMessage());
    }
  }
  
  /**
   * Returns a JSON representation of a PagedResult object (which contains a <b>list of {@link AccountClassification}</b> and optionally,
   * record count) filtered by one of (or a combination of) <b>category, value, rank</b> and paginated according to offset and limit params.
   * Data can be filtered by both exact match and wildcard search (wild character used for search must be specified
   * in the parameter value).
   * @param accountId mandatoy parameter
   * @param category optional (used for filtering)
   * @param value optional (used for filtering)
   * @param rank optional (used for filtering)
   * @param orderBy specifies the order by clause. The value of this query parameter is fieldName[":"("asc"/"desc")].
   *         If there are multiple fields, then comma "," is used to separate them, such as "?orderBy=dname:asc,loc:desc".
   *         Valid fieldNames must are: <i>category</i>, <i>value</i>, <i>rank</i>, <i>startDate</i>, <i>endDate</i>
   * @param offset first row to be returned, zero based
   * @param limit number of rows to be returned
   * @param totalResults true if record count is needed
   * @return a PagedResult JSON object (which contains a <b>list of {@link AccountClassification}</b> and optionally, record count)
   * @throws ServiceException if backend is not available
   * @throws BusinessException if:
   *          accountId is not specified
   *          offset is negative
   *          limit is < 0 or > max number of records that can be returned by backend
   *          orderBy format is invalid or fieldNames are not a valid sort column (see orderBy for valid sort columns)
   *          no data is returned by backend.
   */
  @GET
  @Produces(MediaType.APPLICATION_JSON + ";charset=utf-8")
  @Path("/accountClassification")
  public PagedResult<AccountClassification> getAccountClassification(@QueryParam("accountId") String accountId,
                                                        @QueryParam("category") String category,
                                                        @QueryParam("value") String value,
                                                        @QueryParam("rank") Integer rank, 
                                                        @QueryParam("orderBy") String orderBy, 
                                                        @QueryParam("offset") String offset, 
                                                        @QueryParam("limit") String limit, 
                                                        @QueryParam("totalResults") String totalResults) 
                                                        throws BusinessException, ServiceException {
    if (StringUtils.isEmpty(accountId)) {
      LOGGER.warning("Missing mandatory query param: accountId");
      throw new BusinessException(ErrorCodes.INVALID_ARGUMENT, "AccountId is mandatory");
    }

    PagedResult<AccountClassification> result = null;
    try {
      SrCreateServiceLocal service = SrServiceLocator.getSrCreateService();
      AccountClassification filter = new AccountClassification();
      filter.setCategory(category);
      filter.setValue(value);
      filter.setRank(rank);
      result = service.getAccountClassification(accountId, new PagedFilter<AccountClassification>(filter, parseOffset(offset), parseLimit(limit), getSortCriteria(orderBy), "true".equalsIgnoreCase(totalResults)));
      if (result == null) {
        LOGGER.info("No results retuned by backend");
        throw new BusinessException(ErrorCodes.NO_DATA, "No account info returned");
      }    
      return result;
    } catch (BusinessException be) {
      throw be;
    } catch(ServiceException se) {
      throw se;
    } catch(Exception e) {
      LOGGER.severe("Unexpected error occured while returning backend data.");
      throw new ServiceException(ErrorCodes.INTERNAL_ERROR, e.getMessage());
    }
    
  }
  
  /**
   * Returns a JSON representation of a PagedResult object (which contains a <b>list of {@link Address}</b> and optionally,
   * record count) filtered by one of (or a combination of) <b>city, state, country, streetAddress1</b> and paginated according to offset and limit params.
   * Data can be filtered by both exact match and wildcard search (wild character used for search must be specified
   * in the parameter value).
   * @param accountId mandatory parameter
   * @param city optional (used for filtering)
   * @param state optional (used for filtering)
   * @param country optional (used for filtering)
   * @param streetAddress1
   * @param orderBy specifies the order by clause. The value of this query parameter is fieldName[":"("asc"/"desc")].
   *         If there are multiple fields, then comma "," is used to separate them, such as "?orderBy=dname:asc,loc:desc".
   *         Valid fieldNames must are: <i>streetAddress1</i>, <i>streetAddress2</i>, <i>city</i>, <i>county</i>, <i>state</i>,
   *         <i>province</i>, <i>postalCode</i>, <i>country</i>, <i>timezone</i>, <i>integrationId</i>
   * @param offset first row to be returned, zero based
   * @param limit number of rows to be returned
   * @param totalResults true if record count is needed
   * @return a PagedResult JSON object (which contains a <b>list of {@link Address}</b> and optionally, record count)
   * @throws ServiceException if backend is not available
   * @throws BusinessException if:
   *          accountId is not specified
   *          offset is negative
   *          limit is < 0 or > max number of records that can be returned by backend
   *          orderBy format is invalid or fieldNames are not a valid sort column (see orderBy for valid sort columns)
   *          no data is returned by backend.
   */
  @GET
  @Produces(MediaType.APPLICATION_JSON + ";charset=utf-8")
  @Path("/hardwareAddressByAccount")
  public PagedResult<Address> getHardwareAddressByAccount(@QueryParam("accountId") String accountId,
                                                          @QueryParam("city") String city,
                                                          @QueryParam("state") String state,
                                                          @QueryParam("country") String country, 
                                                          @QueryParam("streetAddress1") String streetAddress1, 
                                                          @QueryParam("orderBy") String orderBy, 
                                                          @QueryParam("offset") String offset, 
                                                          @QueryParam("limit") String limit, 
                                                          @QueryParam("totalResults") String totalResults) 
                                                         throws BusinessException, ServiceException {
    if (StringUtils.isEmpty(accountId)) {
      LOGGER.warning("Missing mandatory query param: accountId");
      throw new BusinessException(ErrorCodes.INVALID_ARGUMENT, "AccountId is mandatory");
    }

    PagedResult<Address> result = null;
    try {
      SrCreateServiceLocal service = SrServiceLocator.getSrCreateService();
      Address filter = new Address();
      filter.setCity(city);
      filter.setState(state);
      filter.setCountry(country);
      filter.setStreetAddress1(streetAddress1);
      result = service.getHardwareAddressByAccount(accountId, new PagedFilter<Address>(filter, parseOffset(offset), parseLimit(limit), getSortCriteria(orderBy), "true".equalsIgnoreCase(totalResults)));
      if (result == null) {
        LOGGER.info("No results retuned by backend");
        throw new BusinessException(ErrorCodes.NO_DATA, "No account info returned");
      }
      return result;
    } catch (BusinessException be) {
      throw be;
    } catch(ServiceException se) {
      throw se;
    } catch(Exception e) {
      LOGGER.severe("Unexpected error occured while returning backend data.");
      throw new ServiceException(ErrorCodes.INTERNAL_ERROR, e.getMessage());
    }
  }
  
  /**
   * Creates an association between a Csi and a contact
   * @param contactId the identifier of the contact that will be associatated to the CSI entity
   * @param csiId the identifier of the CSI to be associated
   * @return a JSON representation of the <b>"SUCCESS"</b> {@link String} if the opperation succeeded
   * @throws ServiceException if backend is not available or an exception occured in the backend
   * @throws BusinessException if:
   *          <li>contactId or csiId is not specified, empty or null<li/>
   *          <li>contact identified by the contactId parameter does not exists<li/>
   *          <li>csi identified by the csiId parameter does not exists<li/>
   *          
   */
  @POST
  @Produces(MediaType.APPLICATION_JSON + ";charset=utf-8")
  @Path("/associateContactToCsi")
  public Response associateContactToCsi(@QueryParam("contactId") String contactId, 
                                        @QueryParam("csiId") String csiId) throws BusinessException, ServiceException {
    if (StringUtils.isEmpty(contactId) || StringUtils.isEmpty(csiId)) {
      LOGGER.warning("Missing mandatory query param (contactId and csiId) was not specified");
      throw new BusinessException(ErrorCodes.INVALID_ARGUMENT, "Both of contactId and csiId params must be specified");
    }

    try {
      SrServiceLocator.getSrCreateService().associateContactToCsi(contactId, csiId);
      return Response.status(Response.Status.CREATED).build(); 
    } catch (BusinessException be) {
      throw be;
    } catch(ServiceException se) {
      throw se;
    } catch(Exception e) {
      LOGGER.severe("Unexpected error occured while returning backend data.");
      throw new ServiceException(ErrorCodes.INTERNAL_ERROR, e.getMessage());
    }
  }

   /**
    * Checks the existence of an association between a Csi and a contact
    * @param contactId the identifier of the contact associatated to the CSI entity
    * @param csiId the identifier of the CSI associated
    * @return a JSON representation of an {@link PagedResult} object containing only the {@link Csi} identified by the 
    * csiId parameter if the association exists or an empty list of {@link Csi} objects if the association does not exists
    * @throws ServiceException if backend is not available
    * @throws BusinessException if:
    *          <li>contactId or csiId is not specified, empty or null<li/>
    *          <li>contact identified by the contactId parameter does not exists<li/>
    *          <li>csi identified by the csiId parameter does not exists<li/>
    *          <li>the association already exists<li/>
    *          
    */
  @GET
  @Produces(MediaType.APPLICATION_JSON + ";charset=utf-8")
  @Path("/checkContactAssociation")
  public PagedResult<Csi> getContactAssociationsToCsis(@QueryParam("contactId") String contactId, 
                                                       @QueryParam("csiId") String csiId) 
                                                       throws BusinessException, ServiceException {
    if (StringUtils.isEmpty(contactId) || StringUtils.isEmpty(csiId)) {
      LOGGER.warning("Missing mandatory query param (contactId and csiId) was not specified");
      throw new BusinessException(ErrorCodes.INVALID_ARGUMENT, "Both of contactId and csiId params must be specified");
    }
    try {
      return SrServiceLocator.getSrCreateService().checkAssociationContactToCsi(contactId, csiId);
    } catch (BusinessException be) {
      throw be;
    } catch(ServiceException se) {
      throw se;
    } catch(Exception e) {
      LOGGER.severe("Unexpected error occured while returning backend data.");
      throw new ServiceException(ErrorCodes.INTERNAL_ERROR, e.getMessage());
    }
  }
  
   /**
    * Returns a list of {@link ProductTemplate} along with the total number of elements
    * @param id the Id of the product Template to be returned
    * @param template the template name of the object to be returned 
    * @param description the description of the Product Template
    * @param hardwareSoftware is the flag that specifies that this product template reffers to Hardware, Software or both
    * @param type the mandatory parameter flag of the type object that must be one of the following: Technical or Non-Techincal 
    * @param orderBy the sets the query orderby clause and must contain only the following Strings: 
    * id, template, description, type, hardwareSoftware; along with the ascending flag
    * @return a {@link PagedResult} object containing the list of {@link ProductTemplate} and a total result count if the filter 
    * has the totalResultsNeeded flag set to true
    * @throws BusinessException if:
    *   Type param is null or empty
    *   <b> or </b><br/>
    *   the filter given as parameter is null <br/>
    *   <b> or </b><br/>
    *   the sort criteria contains field names that the {@link ProductTemplate} objects does not have
    * @throws ServiceException if:
    *   an unexpected error occured while trying to query the backend
    */
  @GET
  @Produces(MediaType.APPLICATION_JSON + ";charset=utf-8")
  @Path("/productTemplates/{type}")
  public PagedResult<ProductTemplate> getProductTemplates(@PathParam("type") String type,
                                                          @QueryParam("id") String id,
                                                          @QueryParam("template") String template,
                                                          @QueryParam("description") String description,
                                                          @QueryParam("hardwareSoftware") String hardwareSoftware, 
                                                          @QueryParam("orderBy") String orderBy, 
                                                          @QueryParam("offset") String offset, 
                                                          @QueryParam("limit") String limit, 
                                                          @QueryParam("totalResults") String totalResults) 
                                                          throws BusinessException, ServiceException {

    try {
      ProductTemplate prodTemplate = new ProductTemplate();
      prodTemplate.setId(id);
      prodTemplate.setTemplate(template);
      prodTemplate.setType(type);
      prodTemplate.setDescription(description);
      prodTemplate.setHardwareSoftware(hardwareSoftware);
      PagedFilter<ProductTemplate> filter = new PagedFilter<ProductTemplate>(prodTemplate, parseOffset(offset), 
                                                                            parseLimit(limit), getSortCriteria(orderBy), 
                                                                            "true".equalsIgnoreCase(totalResults));
      PagedResult<ProductTemplate> result =  SrServiceLocator.getSrCreateService().getProductTemplate(filter);
      if (result == null) {
        LOGGER.info("No results retuned by backend");
        throw new BusinessException(ErrorCodes.NO_DATA, "No product templates returned");
      }
      return result;
    } catch (BusinessException be) {
      throw be;
    } catch(ServiceException se) {
      throw se;
    } catch(Exception e) {
      LOGGER.severe("Unexpected error occured while returning backend data.");
      throw new ServiceException(ErrorCodes.INTERNAL_ERROR, e.getMessage());
    }
   } 

  /**
   * Returns a list of {@link ProductTemplateClarification} associated to a given Product Template along with the total number of elements
   * @param templateId the Id of the product Template associated to the clarifications to be returned
   * @param name the clarification name of the object to be returned 
   * @param hardwareSoftware is the flag that specifies that this product template clarification refers to Hardware, Software or both
   * @param orderBy the sets the query orderby clause and must contain only the following Strings: 
   * name, description, hardwareSoftware; along with the ascending flag
   * @return a {@link PagedResult} object containing the list of {@link ProductTemplateClarification} and a total result count if the filter 
   * has the totalResultsNeeded flag set to true
   * @throws BusinessException if:
   *   Type param is null or empty
   *   <b> or </b><br/>
   *   the filter given as parameter is null <br/>
   *   <b> or </b><br/>
   *   the sort criteria contains field names that the {@link ProductTemplateClarification} objects does not have
   * @throws ServiceException if:
   *   an unexpected error occured while trying to query the backend
   */
  @GET
  @Produces(MediaType.APPLICATION_JSON + ";charset=utf-8")
  @Path("/templateClarifications/{templateId}")
  public PagedResult<ProductTemplateClarification> getProductTemplateClarification(@PathParam("templateId") String templateId,
                                                                                   @QueryParam("name") String name,
                                                                                   @QueryParam("hardwareSoftware") String hardwareSoftware,
                                                                                   @QueryParam("orderBy") String orderBy, 
                                                                                   @QueryParam("offset") String offset, 
                                                                                   @QueryParam("limit") String limit, 
                                                                                   @QueryParam("totalResults") String totalResults) 
                                                                                   throws ServiceException, BusinessException {
    try {
      ProductTemplateClarification prodTmplClarif = new ProductTemplateClarification();
      prodTmplClarif.setName(name);
      prodTmplClarif.setHardwareSoftware(hardwareSoftware);
      prodTmplClarif.setTemplateID(templateId);
      PagedFilter<ProductTemplateClarification> filter = new PagedFilter<ProductTemplateClarification>(prodTmplClarif, parseOffset(offset), 
                                                                            parseLimit(limit), getSortCriteria(orderBy), 
                                                                            "true".equalsIgnoreCase(totalResults));
      PagedResult<ProductTemplateClarification> result = SrServiceLocator.getSrCreateService().getProductTemplateClarification(filter);
      if (result == null) {
        LOGGER.info("No results retuned by backend");
        throw new BusinessException(ErrorCodes.NO_DATA, "No product templates clarification returned");
      }
      return result;
    } catch (BusinessException be) {
      throw be;
    } catch(ServiceException se) {
      throw se;
    } catch(Exception e) {
      LOGGER.severe("Unexpected error occured while returning backend data.");
      throw new ServiceException(ErrorCodes.INTERNAL_ERROR, e.getMessage());
    }
    
  }
  
  /**
   * @param csi on demand csi number (mandatory)
   * @return [name, value] pairs for CMDB configurations (services, environments, instances) for OnDemand CSIs
   * @throws ServiceException if backend is not available or an exception occured in the backend
   * @throws BusinessException if:
   *   csi param is null or empty
   *   <b> or </b><br/>
   *   no results are returned by backend <br/>
   */
  @GET
  @Produces(MediaType.APPLICATION_JSON + ";charset=utf-8")
  @Path("/cmdbConfig/{csi}")
  public ListOfValues getCmdbConfig(@PathParam("csi") String csi) throws ServiceException, BusinessException {
    try {
      ListOfValues result = SrServiceLocator.getSrCreateService().getCmdbConfig(csi);
      if (result == null) {
        LOGGER.info("No results retuned by backend");
        throw new BusinessException(ErrorCodes.NO_DATA, "No cmdb configs returned");
      }
      return result;
    } catch (BusinessException be) {
      throw be;
    } catch(ServiceException se) {
      throw se;
    } catch(Exception e) {
      LOGGER.severe("Unexpected error occured while returning backend data.");
      throw new ServiceException(ErrorCodes.INTERNAL_ERROR, e.getMessage());
    }
  }
  
  /**
   * POST operation that creates a new SR.
   * It consumes a JSON representations of a {@link SR} which is deserialized by Jersey into a {@link SR} POJO 
   * and returns a [srId, modId] pair for newly created SR.
   * This method does not perform any extensive validation of input parameters (e.g. check for each SR type that the mandatory fields are filled in)
   * because it doesn't have all necessary information (e.g. CSI type, productId validity). Full create SR validation is performed in the backend.
   * @param sr POJO populated with SR information
   * @return a [srId, modId] pair for newly created SR
   * @throws BusinessException if:
   *   sr is null 
   *   <b> or </b><br/>
   *   sr is not inserted in the backend <br/>
   * @throws ServiceException if backend is not available or an unexpected exception occured in the backend
   */
  @POST
  @Consumes(MediaType.APPLICATION_JSON + ";charset=utf-8")
  @Produces(MediaType.APPLICATION_JSON + ";charset=utf-8")
  @Path("/createSr")
  public Response createSr(SR sr) throws BusinessException, ServiceException {
    if (sr == null) {
      LOGGER.warning("Missing mandatory insert param: sr");
      throw new BusinessException(ErrorCodes.INVALID_ARGUMENT, "sr is mandatory");
    }
    try {
      CreateSrResult result = SrServiceLocator.getSrCreateService().createSr(sr);
      if (result == null || result.getSrId() == null) {
        LOGGER.info("SR was not created");
        throw new BusinessException(ErrorCodes.NO_DATA, "SR was not created");
      }
      return Response.status(Response.Status.CREATED).entity(result).build();     
    } catch (BusinessException be) {
      throw be;
    } catch (ServiceException se) {
      throw se;
    } catch (Exception e) {
      LOGGER.severe("Unexpected error occured while inserting SR.");
      throw new ServiceException(ErrorCodes.INTERNAL_ERROR, e.getMessage());
    }
  }

  /**
   * Associates a new language to a SR.
   * @param srId
   * @param languageId
   * @return productLanguageId for newly inserted language
   * @throws BusinessException if:
   *   srId and languageId are null or empty
   *   <b> or </b><br/>
   *   language is not asscoiated to the sr <br/>
   * @throws ServiceException if backend is not available or an unexpected exception occured in the backend
   */
  @POST
  @Produces(MediaType.APPLICATION_JSON + ";charset=utf-8")
  @Path("/insertProductLanguage")
  public Response insertProductLanguage(@QueryParam("srId") String srId, 
                                      @QueryParam("languageId") String languageId) throws BusinessException, ServiceException {
    if (StringUtils.isEmpty(srId) && StringUtils.isEmpty(languageId)) {
      LOGGER.warning("Missing mandatory insert param: srId and languageId");
      throw new BusinessException(ErrorCodes.INVALID_ARGUMENT, "srId and languageId are mandatory");
    }

    try {
      String productLanguageId = SrServiceLocator.getSrCreateService().insertProductLanguage(srId, languageId);
      if (productLanguageId == null) {
        LOGGER.info("Product language " + languageId + " was not inserted for SR " + srId);
        throw new BusinessException(ErrorCodes.NO_DATA, "Product language was not inserted");
      }
      return Response.status(Response.Status.CREATED).entity("{\"id\": \"" + productLanguageId + "\"}").build();     
    } catch (BusinessException be) {
      throw be;
    } catch (ServiceException se) {
      throw se;
    } catch (Exception e) {
      LOGGER.severe("Unexpected error occured while inserting product language.");
      throw new ServiceException(ErrorCodes.INTERNAL_ERROR, e.getMessage());
    }
  }

}
