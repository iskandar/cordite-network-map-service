import React from 'react';
import PropTypes from 'prop-types';

export const Swagger = (props) => {
  return(
    <div className='swagger-component'>
      <iframe 
      title="Swagger API" src="https://network-map-test.cordite.foundation/swagger/#/admin/post_admin_api_login" />
    </div>
  );
}