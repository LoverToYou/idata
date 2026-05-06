package com.idata.dto;

import lombok.Data;

/**
 * Extra datasource connection properties
 */
@Data
public class DatasourceProps {
    private String hiveAuth; // KERBEROS / NONE
    private String hivePrincipal;
    private String hiveKeytab;
    private String charset;
    private Integer connectTimeout;
    private Integer socketTimeout;
}
