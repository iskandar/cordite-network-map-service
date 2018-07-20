import React from 'react';

export const Nav = (props) => {
  return(
  <div className="navbar nav-component">
    <div className="row">
      <PageTitle />
      <Icon 
        icon="sign-out"
        toggleModal={props.toggleModal} />
    </div>
  </div>
  )
}

const PageTitle = (props) => {
  return(
    <div className="page-title-component column column-90 col-site-title">
      <a href="#" className="site-title float-left">
        <img src="png/logo-watermark.png" alt="logo" />
      </a>
    </div>
  );
}

class Search extends React.Component{
  constructor(props){
    super(props);
    this.state = {
      value: ""
    }

    this.handleChange = this.handleChange.bind(this)
  }

  handleChange(e){
    this.setState({ value: e.target.value })
  }

  render(){
    return(
      <div className="search-component column column-40 col-search">
        <a 
          href="#" 
          className="search-btn fa fa-search">
        </a>
        <input 
          type="text" 
          name="" 
          value={this.state.value}
          onChange={e => this.handleChange(e)} 
          placeholder="Search..." />
      </div>
    );
  }
} 

const User = (props) => {
  return(
    <div className="column column-30">
      <div className="user-section">
        <a href="#">
          <img src="http://via.placeholder.com/50x50" alt="profile photo" className="circle float-left profile-photo" width="50" height="auto" />
          <div className="username">
            <h4>Jane Donovan</h4>
            <p>Administrator</p>
          </div>
        </a>
      </div>
    </div>
  );
}

const Icon = (props) => {
  return(
    <div className="icon-component column column-10"> 
      <a href="#" onClick={e => props.toggleModal(e)}>
        <em className={"fa fa-" + props.icon}></em>
      </a>
    </div>
  )
}    