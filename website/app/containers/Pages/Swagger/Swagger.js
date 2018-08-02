import React from 'react';

export const Swagger = (props) => {
  return(
    <div className='swagger-component'>
      <iframe 
      title="Swagger API" src="/swagger/#/admin/post_admin_api_login" />
    </div>
  );
}