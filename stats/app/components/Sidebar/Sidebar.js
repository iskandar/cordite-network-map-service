import React from 'react';

export const Sidebar = (props) => {
  return(
    <div id="sidebar" className="column sidebar-component">
      <ul>
        {
          props.navOptions[0].map((option, index) => {
            return(
              <li key={index}>
                <NavLink title={option.title} icon={option.icon} handleBtn={props.handleBtn} />
              </li>
            );
          }, {props: props})
        }
      </ul>
      <h5 className='title-override'>APIs</h5>
      <ul>
        {
          props.navOptions[1].map(function(option, index){
            return(
              <li key={index}>
                <NavLink title={option.title} icon={option.icon} handleBtn={this.props.handleBtn} />
              </li>
            );
          }, {props: props})
        }
      </ul>
    </div>
  );
}

const NavLink = (props) => {
  return(
    <button className='sidebar-button-component' data-btn={props.title} onClick={e => props.handleBtn(e)}>
      <em className={`fa ${props.icon}`}></em>
      {props.title.toUpperCase()}
    </button>
  );
}