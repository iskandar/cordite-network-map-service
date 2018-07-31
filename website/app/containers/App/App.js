import React from 'react';
import Default from 'containers/Default/Default';
import { Login } from 'containers/Login/Login'
import { login, checkAuth } from 'scripts/restCalls';
import { LoginModal, LogoutModal } from 'components/Modal/Modal';

export default class App extends React.Component {
  constructor(props) {
    super(props)
    this.state = {
      admin: false,
      status: 'done',
      subDomain: 'default',
      style: false,
      modal: ''
    }

    this.NMSLogin = this.NMSLogin.bind(this);
    this.toggleModal = this.toggleModal.bind(this);
  }
  

  NMSLogin(loginData){
    return login(loginData)
    .then( () => checkAuth() )
    .then( status => {
      if(status != 200)  return "fail";
      return "success";
    })
    .catch( err => console.log(err) )
  }

  toggleModal(e){
    if(!e){
      this.setState({
        modal: '',
        style: !this.state.style
      }) 
    }
    else if(e.target.dataset.link){
      this.setState({
        modal: e.target.dataset.link || '',
        style: !this.state.style
      })    
    }
  }

  render(){
    let page = null;
    switch(this.state.subDomain){
      case 'login':
        page = <Login nmsLogin={this.NMSLogin} />;
        break;
      case 'default':
        page = <Default 
                toggleModal={this.toggleModal} 
                admin={this.state.admin} />;
        break;
      default: 
        page = <Login nmsLogin={this.nmsLogin} />;
        break;
    }

    let modal = null;
    switch(this.state.modal){
      case 'sign-in':
        modal = <LoginModal
                  toggleModal={this.toggleModal}
                  style={this.state.style} 
                  nmsLogin={this.NMSLogin}/>
        break;
      case 'sign-out':
        modal = <LogoutModal 
                  toggleModal={this.toggleModal}
                  style={this.state.style} 
                  isAuthorised={this.isAuthorised} />
        break;
      default:
        modal = "";
        break;
    }
    
    return (
      <div className='app-component'>
        { (this.state.status == 'done') ? page :  "" }
        { modal }
      </div>    
    );
  }
}